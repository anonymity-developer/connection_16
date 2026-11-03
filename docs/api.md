# API 계약 계획

## 1. 공통 규칙

기본 경로는 `/api/v1`, JSON 응답, 서버 세션 쿠키, UTC ISO-8601 시각을 사용한다. 브라우저는 동일 출처로 요청하며 변경 요청은 CSRF 헤더가 필요하다. 현재 API는 아래 두 개뿐이며, 나머지 표는 개발 계획이다.

| Method | 현재 구현된 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/meta` | 불필요 | 서비스명, 초기 구성 stage, 5개 미션 정의 |
| GET | `/csrf` | 불필요 | CSRF 토큰 및 헤더명, XSRF-TOKEN 쿠키 생성 |

Actuator의 `/actuator/health`, `/actuator/health/readiness`는 컨테이너/로컬 운영 점검용이다. `/api/v1/meta`는 DB 상태 검사가 아니며 readiness가 DB/Redis 연결을 검사한다.

인증 오류는 `{ "code": "UNAUTHENTICATED", "message": "로그인이 필요합니다." }` 형식이다. 도메인 오류에는 `fieldErrors` 또는 `requestId`를 후속 공통 예외 처리 단계에서 추가한다. 빈 성공 응답은 204, 생성은 201, 검수 대기는 202를 사용한다.

| 상태 코드 | 예정 의미 |
| --- | --- |
| 400 | 입력/선택지/토큰 형식 오류 |
| 401 | 로그인 필요 또는 유효하지 않은 인증 |
| 403 | 권한 없음, 미션 미해금, CSRF 실패 |
| 404 | 대상 없음 또는 비공개 대상을 감추는 응답 |
| 409 | 슬롯 소진, 같은 멱등 키의 다른 요청, 상태 충돌 |
| 422 | 제출 형식은 유효하나 콘텐츠 정책 위반 |
| 429 | 발송/입력/제출 속도 제한; Retry-After 제공 |
| 503 | 외부 인증 API 장애 등 일시적 처리 불가 |

## 2. 인증과 프로필 — 예정

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| POST | `/auth/otp/request` | username → challengeId, expiresIn; 계정 유무 공통 응답 |
| POST | `/auth/otp/verify` | challengeId, code → 세션 쿠키 및 onboardingRequired |
| POST | `/auth/logout` | 세션 무효화 → 204 |
| GET | `/users/me` | 내 계정/프로필/필요한 동의 상태 |
| PUT | `/users/me/profile` | nickname, bio, interestIds, githubUrl?, blogUrl?, isPublic, consentVersion |
| GET | `/profiles` | 공개 목록; cursor, limit, interestId, typeCode 필터 |
| GET | `/interests` | 관심사 ID/이름 목록 |

OTP 경로는 구현 단계에서만 permitAll에 추가한다. permitAll도 CSRF와 rate limit을 적용한다. GET `/users/{id}`를 별도로 만들면 방문 기록을 우회하기 쉬우므로 **상세 정보는 아래 POST open 경로로 통일**한다.

`code`는 **숫자 6자리 문자열**이며 `^[0-9]{6}$`로 검증한다. 숫자 타입으로 변환하지 않아 앞자리 0을 유지한다. 영문/기호는 허용하지 않는다. 인증번호는 매 발급 시 안전하게 새로 생성하는 일회용 값이며 고정 공용 번호가 아니다. challenge ID는 인증 요청을 식별하는 별도 값으로, 사용자가 입력하는 인증번호와 구분한다.

검증 요청 예시(형식 설명용 값):

```json
{
  "challengeId": "<발급받은-challenge-id>",
  "code": "052814"
}
```

인증번호는 JSON 본문으로만 보내고 URL/로그에 남기지 않는다. 만료/재발송 대기 안내에 필요한 요청 응답 필드는 인증 단계에서 OpenAPI로 확정한다.

## 3. 태그와 미션 — 예정

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| POST | `/tags/resolve` | token → missionCode, 상태, 진입 경로 |
| GET | `/missions` | 해금 여부, 완료 여부, 힌트 |
| GET | `/missions/collection` | SHOW_ME/SYNC_ME를 구분한 5칸 수집판 |
| GET | `/missions/completion` | 전체 완료, 완료 시각, 운영상 순번, 시상 미확정 여부 |

클라이언트에 직접 미션을 완료하는 범용 POST API를 제공하지 않는다. 각 기능의 서버 검증 결과가 내부 완료 서비스를 호출한다.

### 태그 진입 및 해금 계약

- NFC/QR의 `/t/{token}`은 SPA 진입 경로이며 API가 아니다. 토큰은 무작위 32바이트를 padding 없는 Base64URL로 인코딩한 43자리 값이다. 서버는 `^[A-Za-z0-9_-]{43}$`로 형식을 검사하고 SHA-256 해시로 태그를 조회한다.
- `POST /api/v1/tags/resolve`는 로그인 세션과 CSRF 헤더를 요구한다. 요청 본문은 `{ "token": "<태그에서-읽은-43자리-토큰>" }`이며 이 예시의 자리표시자는 실제 유효 토큰이 아니다. 미션 코드나 ID로 토큰을 대체할 수 없다.
- 공개 메인 페이지 접속은 참여 권한을 부여하지 않는다. 인증 전 태그 진입은 로그인 후 이어서 처리하고, 인증되지 않은 resolve는 401로 거절한다.
- 형식 오류는 400, 형식은 맞지만 없거나 비활성인 태그는 구분하지 않고 404로 응답한다. 미션 비활성/행사 기간 외 요청은 403으로 거절한다. 이미 해금한 유효 태그는 현재 상태를 반환한다.
- 미션 목록/수집판은 상태와 힌트를 보여줄 수 있지만 **각 미션 수행 API는 해당 사용자의 해금 기록을 서버에서 다시 확인**한다. 미해금 상태의 직접 요청은 403이다. 화면 잠금만으로 검사하지 않는다.
- 성공 응답과 목록에는 원문 토큰이나 다른 태그 토큰을 포함하지 않는다. 추측하기 어려운 태그 토큰을 사용하되 일반 API 경로는 의미가 드러나는 이름으로 유지한다. 태그 URL 공유를 통한 해금은 가능하며 현장 체류를 증명하지 않는다.

## 4. 콘텐츠 — 예정

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| POST | `/wishes` | content → 202, 슬롯/revision ID 및 검수 상태 |
| GET | `/wishes` | 승인된 공개 목록, 커서 |
| GET | `/wishes/{id}` | 승인된 상세 정보 |
| GET | `/wishes/me` | 내 슬롯/승인본/대기본 상태 |
| PUT | `/wishes/me` | 수정안 → 202 재검수; 운 점수 유지 |
| POST | `/wishes/{id}/comments` | content, revealProfile → 202 검수 대기 |
| GET | `/wishes/{id}/comments` | 승인 댓글만 공개 조회 |
| POST | `/cheers` | content, templateId, style, anonymous → 202 |
| GET | `/cheers` | 승인된 카드 목록 |
| GET | `/cheers/me` | 내 카드와 검수 상태 |
| PUT | `/cheers/me` | 수정안 → 202 재검수 |
| PUT | `/cheers/{id}/like` | 최초 하트, 기존 하트는 그대로 → 현재 상태 |
| DELETE | `/cheers/{id}/like` | 없으면 그대로 → 204 |

최초 작성 재시도는 Idempotency-Key를 사용한다. 같은 key/payload는 같은 리소스를 반환하고 같은 key/다른 payload는 409다. 사용자 슬롯 UNIQUE가 키 변경으로 인한 중복 생성도 막는다. 공개 목록에는 pending/거절 revision이 포함되지 않는다.

대기 상태 응답 예시:

```json
{
  "id": 101,
  "revision": 1,
  "moderationStatus": "QUEUED",
  "missionCompleted": false
}
```

## 5. 게임과 성향 — 예정

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| POST | `/tap/start` | Idempotency-Key → attemptId, startsAt, endsAt, submitDeadline |
| POST | `/tap/cancel` | attemptId → 시작 전 취소만 기회 복구 |
| POST | `/tap/finish` | attemptId, events, interruptions → 확정/보류 결과 |
| GET | `/tap/me` | 공식 슬롯과 현재 도전 상태 복구 |
| GET | `/tap/rankings` | 유효 점수/잠정 여부/커서 |
| GET | `/sync/questions` | 버전 및 문항/선택지 |
| POST | `/sync/submit` | questionnaireVersion, answers → 서버 계산 최초 결과 |
| GET | `/sync/me` | 저장된 결과 |
| GET | `/sync/types` | 유형별 공개 설명 |

`tap_count`, 운 점수, 유형 코드를 클라이언트가 확정할 수 없다. finish의 실제 이벤트 포맷/크기 제한은 모바일 검증 후 OpenAPI로 고정한다.

## 6. 동료 탐색 — 예정

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| GET | `/discovery/recommendations` | 공개 추천 목록과 추천 이유 |
| GET | `/discovery/people` | 필터/검색/커서 기반 동료 탐색 |
| POST | `/discovery/profiles/{id}/open` | 공개 상세 반환 + 최초 방문 저장; 재시도 멱등 |
| GET | `/discovery/graph` | 내 발견 관계만, limit 상한 |
| GET | `/discovery/stats` | 현재 노출 가능한 발견/상호 방문 집계 |

상세 프로필 예시:

```json
{
  "profile": {
    "id": 12,
    "nickname": "코딩하는 수달",
    "realName": "참여자",
    "region": "서울",
    "classNo": 1,
    "bio": "백엔드 이야기를 좋아해요.",
    "githubUrl": null,
    "blogUrl": null
  },
  "firstVisit": true,
  "mutual": false
}
```

익명 응원/댓글은 위 프로필로 연결하는 식별자를 반환하지 않는다. 사용자별 API에 no-store를 적용한다.

## 7. 운영자 — 예정

`/admin` 하위 API는 ADMIN 역할을 서버에서 확인한다. 검수 대기 조회 및 판정, 신고 처리, 회원 제한, 게임 재도전 승인, 시상 후보 정산/확정, 행사 설정을 제공한다. 변경 요청에는 사유를 필수로 받고 모든 조치를 감사 로그에 남긴다. 최종 확정은 조회나 페이지 진입만으로 실행하지 않는다.

상세 요청 DTO, validation, 페이지네이션 응답과 OpenAPI 파일은 각 단계 착수 시 함께 작성한다. 이 문서의 계획을 이미 구현된 계약으로 배포하지 않는다.
