# API 계약 계획

## 1. 공통 규칙

기본 경로는 `/api/v1`, JSON 응답, 서버 세션 쿠키, UTC ISO-8601 시각을 사용한다. 브라우저는 동일 출처로 요청하며 변경 요청은 CSRF 헤더가 필요하다. 기본 API는 아래 표에 있으며, 인증, 페이즈 3 참여와 페이즈 4-1 SYNC/FAST TAP API도 구현했다. 그 밖의 도메인은 개발 계획이다. 정확한 스키마는 [인증 OpenAPI](auth.openapi.yaml)와 [참여 OpenAPI](participation.openapi.yaml)를 따른다.

사용자 동선은 [유저 플로우](user-flows.md), 저장 관계는 [전체 ERD](erd.md), 트랜잭션은 [구현 계획](implementation-plan.md)을 따른다. 아래 예시와 추가 경로는 후속 구현 계약이며 현재 호출 가능한 API라고 안내하지 않는다.

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

## 2. 인증과 프로필 구현

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| GET | `/auth/config` | 인증 활성 설정과 비밀번호 길이; 외부 접속 확인 결과는 아님 |
| POST | `/auth/otp/request` | purpose, username(SIGN_UP/RESET만) → challengeId, expiresIn; PASSWORD_CHANGE는 세션의 본인 대상 |
| POST | `/auth/signup` | challengeId, code, password → DM 소유 확인 후 회원/해시 생성, 세션 및 onboardingRequired |
| POST | `/auth/login` | Mattermost username, TAG ME password → 세션 및 onboardingRequired; OTP 불필요 |
| PUT | `/users/me/password` | 세션, challengeId, code, newPassword → 비밀번호 변경/모든 이전 세션 무효화 |
| POST | `/auth/password/reset` | challengeId, code, newPassword → 본인 DM 인증 후 기존 비밀번호 재설정/모든 이전 세션 무효화 |
| POST | `/auth/logout` | 세션 무효화 → 204 |
| GET | `/users/me` | 내 계정/프로필/필요한 동의 상태 |
| PUT | `/users/me/profile` | nickname, realName, classNo, bio, interestIds, githubUrl?, blogUrl?, isPublic, acceptPolicies, expectedVersion |
| GET | `/profiles` | 공개 목록; cursor, limit, interestId, typeCode 필터 |
| GET | `/interests` | 관심사 ID/이름 목록 |

가입/로그인/OTP 발송/분실 재설정은 필요한 POST 경로만 permitAll에 추가했다. PASSWORD_CHANGE 발송과 `/users/me/password`는 세션이 필요하다. permitAll도 CSRF와 rate limit을 적용한다. 일반 로그인은 아이디/비밀번호이며 OTP만으로 로그인하는 `/auth/otp/verify`는 제공하지 않는다. GET `/users/{id}`를 별도로 만들면 방문 기록을 우회하기 쉬우므로 **상세 정보는 아래 POST open 경로로 통일**한다.

위 표 중 공개 프로필 목록 `/profiles`는 예정이며 그 외 경로는 구현했다. `onboardingRequired`는 필수 프로필/관심사/현재 정책 동의/공개 상태를 포함한다. 직접 입력한 이름과 반의 외부 검증을 뜻하지 않는다. 동의/기간/버전 규칙은 [참여 계약](participation.md)을 따른다.

`code`는 **숫자 6자리 문자열**이며 `^[0-9]{6}$`로 검증한다. 숫자 타입으로 변환하지 않아 앞자리 0을 유지한다. 영문/기호는 허용하지 않는다. 인증번호는 매 발급 시 안전하게 새로 생성하는 일회용 값이며 고정 공용 번호가 아니다. challenge ID는 인증 요청을 식별하는 별도 값으로, 사용자가 입력하는 인증번호와 구분한다.

가입 완료 요청 예시(형식 설명용 값):

```json
{
  "challengeId": "<발급받은-challenge-id>",
  "code": "052814",
  "password": "<가입할-때-설정한-TAG-ME-비밀번호>"
}
```

challenge의 purpose는 SIGN_UP/PASSWORD_CHANGE/PASSWORD_RESET이며 계정/작업과 바인딩한다. 가입용 code로 비밀번호를 바꾸거나 다른 계정의 code로 작업하지 못한다. 비밀번호 변경 대상은 세션에서 구한다. 변경/재설정 성공 후 현재/다른 기기 세션을 모두 거절하고 다시 로그인한다. 비밀번호/인증번호는 JSON 본문으로만 보내고 URL/로그/분석/외부 API/응답에 남기지 않는다. 비밀번호 해시도 응답하지 않는다. 전체 인증 계약은 [인증 설계](auth.md)를 따른다.

일반 로그인 요청 예시:

```json
{
  "username": "<Mattermost-username>",
  "password": "<TAG-ME-비밀번호>"
}
```

비밀번호 변경/재설정의 최종 요청 예시(각 API에 맞는 purpose로 발급된 challenge 필요):

```json
{
  "challengeId": "<목적과-계정이-일치하는-challenge-id>",
  "code": "052814",
  "newPassword": "<새-TAG-ME-비밀번호>"
}
```

## 3. 태그와 미션 구현

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| POST | `/tags/resolve` | token → missionCode, 상태, 진입 경로 |
| GET | `/missions` | collection과 같은 5개 미션/개수/전체 완료 응답 |
| GET | `/missions/collection` | SHOW_ME/SYNC_ME를 구분한 5칸 수집판 |
| GET | `/missions/completion` | 전체 완료, 완료 시각, 운영상 순번, 시상 미확정 여부 |

클라이언트에 직접 미션을 완료하는 범용 POST API를 제공하지 않는다. 각 기능의 서버 검증 결과가 내부 완료 서비스를 호출한다.

GET `/event`, `/policies`, `/users/me/profile`, GET/POST `/tags/pending`, POST `/tags/resume`, PUT `/users/me/consents/{type}`도 구현했다. 정책 미설정은 참여 쓰기를 차단하며, 실제 원문/일정/태그는 시드하지 않는다. 상세 응답은 [참여 OpenAPI](participation.openapi.yaml)가 기준이다.

### 태그 진입 및 해금 계약

- NFC/QR의 `/t/{token}`은 SPA 진입 경로이며 API가 아니다. 토큰은 무작위 32바이트를 padding 없는 Base64URL로 인코딩한 43자리 값이다. 서버는 `^[A-Za-z0-9_-]{43}$`로 형식을 검사하고 SHA-256 해시로 태그를 조회한다.
- `POST /api/v1/tags/resolve`는 로그인 세션과 CSRF 헤더를 요구한다. 요청 본문은 `{ "token": "<태그에서-읽은-43자리-토큰>" }`이며 이 예시의 자리표시자는 실제 유효 토큰이 아니다. 미션 코드나 ID로 토큰을 대체할 수 없다.
- 공개 메인 페이지 접속은 참여 권한을 부여하지 않는다. 인증 전 태그 의도는 15분간 서버 세션에 hash로 보관하여 로그인 후 이어서 처리하고, 인증되지 않은 resolve는 401로 거절한다.
- 형식 오류는 400, 형식은 맞지만 없거나 비활성인 태그는 구분하지 않고 404로 응답한다. 미션 비활성/행사 기간 외 요청은 403으로 거절한다. 이미 해금한 유효 태그는 현재 상태를 반환한다.
- 미션 목록/수집판은 상태와 힌트를 보여줄 수 있지만 **각 미션 수행 API는 해당 사용자의 해금 기록을 서버에서 다시 확인**한다. 미해금 상태의 직접 요청은 403이다. 화면 잠금만으로 검사하지 않는다.
- 성공 응답과 목록에는 원문 토큰이나 다른 태그 토큰을 포함하지 않는다. 추측하기 어려운 태그 토큰을 사용하되 일반 API 경로는 의미가 드러나는 이름으로 유지한다. 태그 URL 공유를 통한 해금은 가능하며 현장 체류를 증명하지 않는다.

## 4. 콘텐츠 — 페이즈 4-2 구현 및 후속 범위

| Method | 경로 | 요청/결과 요점 |
| --- | --- | --- |
| POST | `/wishes` | content → 202, 슬롯/revision ID 및 검수 상태 |
| GET | `/wishes` | 승인된 공개 목록, 커서 |
| GET | `/wishes/{id}` | 후속: 승인된 상세 정보 |
| GET | `/wishes/me` | 내 슬롯/승인본/대기본 상태 |
| PUT | `/wishes/me` | 수정안 → 202 재검수; 운 점수 유지 |
| POST | `/wishes/{id}/comments` | 후속: content, revealProfile → 202 검수 대기 |
| GET | `/wishes/{id}/comments` | 후속: 승인 댓글만 공개 조회 |
| POST | `/cheers` | content, templateId, style, anonymous → 202 |
| GET | `/cheers` | 승인된 카드 목록 |
| GET | `/cheers/me` | 내 카드와 검수 상태 |
| PUT | `/cheers/me` | 수정안 → 202 재검수 |
| PUT | `/cheers/{id}/like` | 후속: 최초 하트, 기존 하트는 그대로 → 현재 상태 |
| DELETE | `/cheers/{id}/like` | 후속: 없으면 그대로 → 204 |

최초 작성 재시도는 Idempotency-Key를 사용한다. 같은 key/payload는 같은 리소스를 반환하고 같은 key/다른 payload는 409다. 사용자 슬롯 UNIQUE가 키 변경으로 인한 중복 생성도 막는다. 공개 목록에는 pending/거절 revision이 포함되지 않는다.

아래 초기 응답 예시는 개념 설명이다. 구현된 요청은 content/templateId/style/anonymous/expectedVersion, 응답은 OwnSlot(approvedRevision/pendingRevision/latestRevision/version)이며 [콘텐츠 계약](content.md)과 [OpenAPI](content.openapi.yaml)가 정확한 기준이다.

초기 개념 응답 예시:

```json
{
  "id": 101,
  "revision": 1,
  "moderationStatus": "QUEUED",
  "missionCompleted": false
}
```

## 5. 게임과 성향 구현

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

위 표 중 공개 `/tap/rankings`만 후속이며 그 외 경로는 구현했다. `/tap/start`의 준비/플레이/유예는 5/10/5초다. finish는 attemptId, taps(최대 1000개 상대 ms), interruptions를 받고 count를 서버에서 계산한다. 실제 형식/초기 검토 기준/기한/재시도는 [결과 계약](results.md)과 [결과 OpenAPI](results.openapi.yaml)를 따른다. `tap_count`, 운 점수, 유형 코드는 클라이언트가 확정하지 않는다.

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

### 운영자 경로 상세 계획

모든 경로는 ADMIN과 CSRF(변경 요청)를 확인한다. 변경 본문에 reason을 포함하고 현재 상태 또는 version, 필요한 멱등 키를 확인한다. 아래 명칭은 후속 구현 시 OpenAPI로 고정한다.

| Method | 경로 | 역할 |
| --- | --- | --- |
| GET | `/admin/users` | 회원/계정 상태/참여 이력 검색 |
| PATCH | `/admin/users/{id}/status` | 이용 제한/복구, 사유와 감사 기록 |
| GET | `/admin/missions/progress` | 사용자별 해금/완료/전체 완료 조회 |
| POST | `/admin/tags` | 태그 발급/미션 연결; 원문은 생성 시 한 번만 운영자에게 전달 |
| PATCH | `/admin/tags/{id}` | 활성/비활성/교체 관리; 목록은 원문 미반환 |
| PATCH | `/admin/missions/{id}` | 힌트/활성 상태 변경 |
| GET | `/admin/moderation` | 작업/revision별 대기/실패/검토 목록 |
| POST | `/admin/moderation/{jobId}/decision` | 해당 버전 승인/거절 및 최초 완료 연계 |
| POST | `/admin/moderation/{jobId}/retry` | 재처리 가능 작업의 제한 재요청 |
| GET | `/admin/content` | 종류/상태별 승인본/대기본 조회 |
| PATCH | `/admin/content/{id}/visibility` | 숨김/복구; 검수 승인과 별도 조치 |
| GET | `/admin/reports` | 신고와 처리 상태 목록 |
| POST | `/admin/reports/{id}/resolve` | 신고 판정/조치 및 감사 기록 |
| GET | `/admin/tap/attempts` | 공식 도전/중단/검증 이력 |
| POST | `/admin/tap/attempts/{id}/decision` | 유효/무효 판정; 사유 필수 |
| POST | `/admin/tap/users/{id}/retry` | 승인된 새 generation 생성; 과거 이력 유지 |
| GET | `/admin/awards/runs` | 정산 실행 이력 |
| POST | `/admin/awards/runs` | 마감/규칙 버전 고정 후 후보 snapshot 생성 |
| GET | `/admin/awards/runs/{id}` | 후보와 근거/충돌/잠정 상태 |
| POST | `/admin/awards/runs/{id}/finalize` | 중복/동점/무효 검토 후 최종 확정 |
| GET | `/admin/event/settings` | 행사 시각/게임 설정/버전 |
| PUT | `/admin/event/settings` | version 조건으로 변경, 마감 충돌 검증 |
| GET | `/admin/audit-logs` | 조치자/대상/사유/시각 조회 |

## 8. 공통 인가와 입력 규칙 — 예정

| 요청 종류 | 서버 확인 |
| --- | --- |
| 가입/변경/재설정 OTP | CSRF, 계정/IP 제한, challenge의 계정/purpose/만료/횟수/일회성, 실제 채널 범위; 변경은 본인 세션 필수 |
| 일반 로그인 | username의 외부 내부 ID 연결, TAG ME 비밀번호 해시, 계정/채널 상태, 계정/IP 제한; DM OTP 불필요 |
| 비밀번호 변경/재설정 | 일치하는 OTP와 새 비밀번호 정책, hash/version 트랜잭션, 이전 세션 무효화; 미가입 재설정은 거절 |
| 프로필/내 상태 | 유효 세션/계정, 본인 여부, 공개/동의 정책 |
| 태그 resolve | 세션, CSRF, 토큰 형식/해시/활성, 행사 기간 |
| 미션 수행/내 콘텐츠 수정 | 세션, 해금, 계정 상태, 공식 횟수/기간, 본인 소유, CSRF |
| 소원/응원/댓글 공개 조회 | 세션과 현재 승인본/공개 상태; 목록에 대기본 미포함 |
| 댓글/하트 | 해당 미션 해금, 대상 공개/유효 여부, 마감/중복/자기 활동 규칙 |
| 동료 목록/상세/그래프 | 세션, 현재 프로필 공개 필터, 자기 그래프 범위, 노드/페이지 상한 |
| 관리자 변경 | ADMIN, CSRF, 사유, 상태/버전/멱등성, 감사 기록 |

해금은 미션의 수행 권한이다. 로그인 참여자가 다른 사람의 승인된 소원/응원/프로필을 읽는 것과 자신의 미션을 수행하는 것을 구분한다. 직접 경로 입력은 같은 서버 검사를 받는다. 조회 가능한 데이터가 작성/수정 권한을 의미하지 않는다.

프로필/콘텐츠 문자열은 trim/길이/허용 형식 검사를 서버에서 수행하고 본문을 HTML로 해석하지 않는다. 비밀번호는 임의 trim/소문자 변환 없이 별도의 정책으로 검증한다. 프로필의 닉네임은 현재 DB 최대 30자, bio는 최대 200자이며 필수/최소 길이는 프로필 계약에서 확정한다. 외부 링크는 https만 허용하고 허용되지 않는 스킴을 거절한다. 콘텐츠 길이/스타일 허용목록/이벤트 payload 크기/페이지 상한은 각 도메인의 OpenAPI와 서버 설정에 함께 확정한다.

GET `/users/{id}`처럼 프로필 방문 기록을 우회하는 별도 상세 경로는 제공하지 않는다. 익명 author, Mattermost ID, 원문 태그/OTP/HMAC, 관리자 전용 데이터는 공개 JSON에 넣지 않는다. 추천 이유도 익명 활동자의 신원을 드러내면 안 된다.

## 9. 상태와 멱등성 응답 예시 — 예정

예시는 구조를 설명하는 가상 데이터이며 실제 인증번호/태그나 검증 성과를 뜻하지 않는다. 도메인 API는 아래와 같은 JSON 객체를 기본으로 하고, 현재 구현된 meta/CSRF 응답을 임의의 공통 envelope로 변경하지 않는다.

### 태그 해금 응답

```json
{
  "missionCode": "MAKE_A_WISH",
  "status": "UNLOCKED",
  "unlockedAt": "2026-11-03T04:00:00Z",
  "completedAt": null,
  "route": "/missions/make-a-wish"
}
```

route는 서버가 정한 내부 경로이며 토큰에서 외부 URL을 읽어 반환하지 않는다. 같은 유효 태그 재요청은 최초 unlockedAt을 유지한다. 태그가 비활성화되면 새로운 resolve는 거절하되 기존 미션 진행 행은 자동 삭제하지 않는다.

### 내 소원/응원 슬롯

```json
{
  "id": 101,
  "visibilityStatus": "VISIBLE",
  "approvedRevision": {
    "id": 201,
    "revisionNo": 1,
    "content": "함께 성장하고 싶어요.",
    "moderationStatus": "APPROVED"
  },
  "pendingRevision": {
    "id": 202,
    "revisionNo": 2,
    "content": "좋은 동료들과 함께 성장하고 싶어요.",
    "moderationStatus": "QUEUED"
  },
  "missionCompleted": true
}
```

위 상태에서 공개 목록에는 revision 201만 노출한다. revision 202가 거절되면 기존 승인본은 유지한다. 사용자 본인과 관리자만 대기본을 볼 수 있다. 최초 검수 중이면 approvedRevision이 null이고 missionCompleted는 false다.

### 게임 시작/종료

```json
{
  "attemptId": 301,
  "serverNow": "2026-11-03T04:00:00Z",
  "startsAt": "2026-11-03T04:00:05Z",
  "endsAt": "2026-11-03T04:00:15Z",
  "submitDeadline": "2026-11-03T04:00:20Z",
  "status": "COUNTDOWN"
}
```

카운트다운 5초/플레이 10초가 기준이다. 위 submitDeadline의 추가 5초는 예시일 뿐 확정 유예가 아니다. 실제 유예는 모바일 전송 검증과 행사 마감 정책으로 정한다. 서버 시작/종료가 달라지는 브라우저 로컬 타이머를 근거로 점수를 인정하지 않는다.

```json
{
  "attemptId": 301,
  "events": [
    { "type": "TAP", "relativeTimeMs": 124 },
    { "type": "TAP", "relativeTimeMs": 261 }
  ],
  "interruptions": []
}
```

종료 이벤트 구조도 예정안이다. 서버가 이벤트 수/순서/시각/크기/중단을 검증해 점수를 계산한다. 같은 종료 payload 재전송은 저장된 판정을 반환하고 다른 payload로 기존 결과를 덮어쓰려 하면 409다. 검토 보류와 최초 완료는 분리한다.

### 방문 그래프

```json
{
  "nodes": [
    { "id": 1, "nickname": "ME", "isMe": true },
    { "id": 12, "nickname": "코딩하는 수달", "isMe": false },
    { "id": 23, "nickname": "도전하는 감자", "isMe": false }
  ],
  "edges": [
    { "source": 1, "target": 12, "mutual": true },
    { "source": 12, "target": 1, "mutual": true },
    { "source": 1, "target": 23, "mutual": false }
  ],
  "stats": { "discovered": 2, "discoveredBy": 1, "mutual": 1 },
  "hasMore": false
}
```

내가 방문했거나 나를 방문한 공개 가능한 동료와 본인에 직접 연결된 방향 간선만 반환한다. 상호 관계는 UI에서 두 방향을 한 굵은 연결로 합쳐 표현할 수 있다. 동료들 사이의 별도 간선은 반환하지 않는다. stats는 제한된 화면 노드 수와 구분한 전체 적격 집계이며 비공개 필터를 동일하게 적용한다.

### 오류

```json
{
  "code": "MISSION_LOCKED",
  "message": "해당 미션의 태그를 먼저 찾아주세요.",
  "requestId": "<서버-요청-id>"
}
```

오류 응답에 stack trace/SQL/외부 비밀키/인증번호/태그 원문을 넣지 않는다. 검수 거절 사유는 수정에 필요한 범위로 전달한다. 권한 없는 사용자가 비공개 대상의 존재 여부를 확인할 수 없도록 일반 404를 사용할 수 있다.

## 10. 페이지네이션, 캐시, 중복 제출 — 예정

- 목록은 `cursor`, `limit`, 필터를 받고 `{ "items": [], "nextCursor": null, "hasMore": false }` 형태를 기본안으로 한다. 커서는 불투명 값이며 클라이언트가 SQL 조건을 전달하는 수단이 아니다.
- 사용자별 상태/프로필/그래프는 `Cache-Control: no-store`를 사용한다. 정적 유형 설명/문항을 캐시한다면 버전과 공개 범위를 먼저 정한다.
- 최초 슬롯 생성과 게임 시작에는 `Idempotency-Key`를 사용한다. 서버는 사용자/작업/payload hash를 묶어 검사하며 같은 키/다른 payload는 409다.
- 하트 등록 PUT/취소 DELETE와 방문 POST open은 재시도해도 중복 기록/점수가 생기지 않아야 한다. 삭제 대상이 없어도 하트 취소는 204다.
- 로그인/로그아웃 후 CSRF를 다시 받는다. 단순 네트워크 재시도만으로 인증번호 재발급, 새 공식 게임, 새 revision을 임의로 생성하지 않는다.
- 중복 제출의 HTTP 상태와 응답 재생 보존 기간은 구현 OpenAPI에서 고정하고 DB 슬롯/복합키 제약을 마지막 방어선으로 유지한다.

## 11. 신고, 동의와 행사 조회 — 추가 계획

| Method | 경로 | 요청/역할 |
| --- | --- | --- |
| GET | `/event` | 공개 행사명/진행 상태/일정/공지 버전; 운영자·개인 데이터 제외 |
| POST | `/content/{id}/reports` | 로그인 사용자의 reason 기반 신고 접수; 과다/중복 제한 |
| GET | `/users/me/consents` | 본인의 목적/버전/동의/철회 상태 |
| PUT | `/users/me/consents/{policyType}` | 버전과 동의/철회; 공개 설정/참여 자격 영향을 함께 처리 |
| POST | `/users/me/withdrawal` | 확인 후 탈퇴 절차; 즉시 공개 중단과 보관 정책 적용 |

현재 permitAll은 이 추가 예정 경로를 허용하지 않는다. 동의 철회/탈퇴의 실제 삭제 범위와 보존 기간은 배포 전 운영 정책으로 확정한다. 신고를 콘텐츠 자동 삭제로 처리하거나, 탈퇴를 모든 감사/시상 데이터의 무조건 cascade 삭제로 구현하지 않는다. 공개 결과 발표 API와 경품 발송은 운영 정책/개인정보 범위를 확정한 후 별도 계약으로 추가한다.
