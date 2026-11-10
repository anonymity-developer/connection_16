# 프로필, 동의, 태그와 수집판 구현

페이즈 3은 인증된 사용자가 자신의 프로필과 동의를 저장하고, 현장 태그로 미션을 해금하며, 서버가 검증한 SHOW ME 완료를 수집판에 반영하는 범위다. 성향/소원/탭/응원과 공개 동료 탐색은 후속 페이즈에서 구현했다. 관리자 설정 화면/배포는 아직 없다.

## 시작 전 운영 설정

V4는 행사 설정, 버전별 동의, 접수 근거와 동의 이력 테이블을 추가한다. 관심사 카테고리만 시드하며 행사 일정, 동의 원문, 참가자와 태그는 넣지 않는다. `event_settings` 행 또는 두 종류의 현재 정책이 없으면 참여 쓰기를 거절한다. 실제 Mattermost 연동의 확인 여부는 [인증](auth.md)과 별개다.

`event_settings.id=1`의 opens_at/submit_closes_at/moderation_closes_at은 UTC DATETIME(6)다. 시작 < 신규 제출 마감 <= 검수 마감을 만족해야 한다. `profile_preparation_enabled=false`가 기본값이며, true로 명시한 경우에만 시작 전 프로필 저장을 허용한다. 태그 해금과 공식 완료는 항상 행사 진행 중에만 가능하다. 종료 후에도 자신의 기록 조회는 가능하다. 참가자 비공개/철회 API는 제공하지 않는다.

운영자는 확정된 문구를 `consent_policies`의 `(policy_type,policy_version)`별 새 행으로 추가하고 `current_consent_policies`에서 현재 버전을 가리킨다. policy_type은 PARTICIPATION/PROFILE_PUBLICATION이다. 기존 원문을 덮어쓰지 않는다. 문구에는 수집/공개 항목, 목적, 보관 기간, 철회/문의 방법을 실제 정책으로 명시해야 하며 이 저장소는 임의의 약관을 확정하지 않는다. 같은 트랜잭션에서 두 현재 포인터를 바꾸고, 운영 DB 권한과 변경 기록은 별도로 관리한다.

설정 예시의 값은 운영자가 검토한 값으로 바꾸어 Git 밖에서 실행한다. 도구가 DB에 자동 접속하거나 운영을 개장하지 않는다.

```sql
START TRANSACTION;
INSERT INTO consent_policies(policy_type,policy_version,title,body)
VALUES ('PARTICIPATION','<검토한-버전>','<검토한-제목>','<검토한-원문>'),
       ('PROFILE_PUBLICATION','<검토한-버전>','<검토한-제목>','<검토한-원문>');
INSERT INTO current_consent_policies(policy_type,policy_version)
VALUES ('PARTICIPATION','<검토한-버전>'),('PROFILE_PUBLICATION','<검토한-버전>');
-- event_settings는 확정한 UTC 일정과 정책에 따라 별도로 INSERT한다.
COMMIT;
```

현재 포인터가 이미 있으면 승인된 변경에서만 해당 행을 갱신한다. `settings_version`도 실제 변경 때 증가시킨다. 서비스는 사용자 잠금 후 설정/현재 정책에 공유 잠금을 걸어 쓰기 중 교체를 직렬화한다. 운영 변경 트랜잭션에서는 사용자 행을 함께 잠그지 않는다.

## 내 프로필과 참여 안내

[가져오기와 공개 계약](mattermost-profile.md)이 현재 기준이다. `GET /users/me/profile`은 가져온 읽기 전용 identity와 수정 가능한 profile, 현재 두 정책/준비 상태를 반환한다. identity는 프로필을 아직 작성하지 않아도 반환하며 ready=false면 재로그인이 필요하다.

닉네임만 필수(최대 30자)다. 한 줄 소개는 선택(최대 200자), 도메인/기술 스택 관심사는 카탈로그 ID 0~10개이며 중복을 거절한다. GitHub/블로그는 선택, HTTPS만 허용한다. GitHub는 github.com만 허용하며 사용자정보/fragment/비표준 포트/제어 문자를 거절한다. 링크를 서버가 조회하지 않는다. 길이는 Jakarta Size의 UTF-16 단위다. 가져온 이름/지역/반/사진과 isPublic을 저장 본문에 받지 않는다.

저장은 expectedVersion을 받아 다른 탭의 변경을 조용히 덮어쓰지 않는다. 신규는 0, 기존은 profile.version이다. 이전 버전은 409 PROFILE_CHANGED다. 저장/안내 확인 이력/관심사/접수/해당 글자 지급은 한 트랜잭션이다. 이름·사진 소스가 바뀌어도 기존 닉네임/소개/관심사/링크는 보존하며 기존 버전을 올린다.

화면에는 참여·상세 공개 안내를 한 번 체크하는 동작만 있다. 현재 PARTICIPATION/PROFILE_PUBLICATION 두 기록을 내부적으로 확인한다. acceptPolicies에는 새로 확인한 현재 버전만 보낸다. 오래된 버전은 409 POLICY_CHANGED이며 자동 승계하지 않는다. 별도 공개 선택/비공개/철회 화면과 PUT consents 경로는 제거했다. 레거시 is_public 컬럼은 내부 호환용이며 유효한 프로필 저장은 항상 공개다. 예전 비공개/미완성 계정을 일괄 공개하지 않는다.

목록에는 닉네임/관심사/성향만, 열린 상세에는 가져온 이름·지역·반·사진과 작성한 소개·링크를 제공한다. 본인 사진 GET과 방문 후 상대 사진 GET은 세션/현재 상태를 검사하고 서버가 Mattermost 이미지를 중계한다. [탐색 계약](discovery.md)과 사진 전달 계약을 따른다.

## 태그와 로그인 복귀

태그 원문은 32바이트 난수의 43자리 Base64URL이다. 서버는 길이/문자뿐 아니라 32바이트 디코딩 후 재인코딩 일치까지 검사한다. DB에는 원문 문자열의 SHA-256만 저장한다. 태그 목록/수집판/오류 응답에는 원문이나 hash를 반환하지 않는다.

1. `/t/{token}` GET은 화면만 연다. 자동 해금이나 상태 변경을 하지 않는다.
2. 미로그인 사용자가 ‘로그인하고 이어가기’를 누르면 CSRF를 포함한 POST `/tags/pending`으로 의도를 저장한다. IP 제한을 적용하고 태그 존재는 조회하지 않는다.
3. 서버 세션에는 원문 대신 hash와 15분 만료만 저장한다. 성공 응답은 202이고 본문이 없다. GET `/tags/pending`은 present만 반환한다.
4. 로그인 시 세션 ID 교체 후에도 이 임시 상태가 남는다. 브라우저 localStorage/sessionStorage에는 자격증명이나 태그를 보관하지 않는다.
5. 프로필 준비가 필요하면 내 계정/프로필부터 진행한다. 이후 고정 내부 경로 `/t/resume`에서 POST `/tags/resume`으로 이어간다. 외부 return URL은 받지 않는다.
6. 로그인한 사용자의 직접 태그는 POST `/tags/resolve`로 처리한다. 세션/CSRF/계정 버전/행사/태그/미션 활성 상태를 검증한다.
7. resolve 성공 후 새 해금 여부/글자 지급 여부와 정해진 내부 미션 경로를 반환한다. 기존 SHOW ME 태그는 해금 근거를 만들지 않고 `/me/profile`로 이동한다. 다른 네 미션은 태그에 연결된 `/missions/{slug}` 하나만 해금한다. 먼저 해금한 미션은 이어서 참여할 수 있다. resume 성공 시 임시 의도를 지운다. 실패 시에는 다시 시도할 수 있고 15분이 지나면 다시 스캔해야 한다.

형식 오류는 400, 미존재/비활성 태그는 같은 404, 행사 외/비활성 미션은 403이다. 사용자 행 잠금과 progress 복합 PK로 중복 해금을 막는다. 첫 해금 시각/접수 ID를 덮어쓰지 않는다. URL 공유로 다른 인증 사용자가 해금할 수 있으므로 현장 체류 증명으로 안내하지 않는다. HTML meta와 운영 Caddy는 no-referrer를 사용한다. 접근 로그/분석 도구에 태그 경로를 수집하지 않는다.

## SHOW ME와 공통 완료

SHOW ME는 NFC 없이 행사 OPEN 중 자동 지급한다. 공개된 필수 프로필 + 비어 있지 않은 소개 + 관심사 1개 이상 + 현재 두 동의가 필요하다. 닉네임만으로 프로필을 저장하고 온보딩을 마칠 수 있지만 보상 조건은 별도다. 프로필 저장 또는 인증·CSRF가 있는 POST `/missions/show-me/reconcile`이 현재 조건을 재검증한다. 사전 등록은 OPEN 이후 POST에서 평가하며 GET 조회는 지급하지 않는다. 이후 수정/재평가/기존 태그 재스캔은 최초 완료 시각을 바꾸지 않는다.

MissionCompletionService는 클라이언트용 범용 완료 API가 아니다. 사용자 행 잠금 → 계정/행사 → 미션 활성/해금 → 서버 receipt 소유 → 해당 미션의 CompletionRule 검증 → 최초 evidence/progress → 다섯 code 판정 → 필요할 때만 전역 sequence 잠금 순서다. 비밀번호 버전과 ACTIVE 상태는 실제 쓰기 트랜잭션 안에서도 확인한다.

운영 CompletionRule은 다섯 미션별 서버 근거를 확인한다. 후속 페이즈에서 성향/공식 게임/소원·응원 접수 근거를 연결했다. 테스트의 가상 미래 rule은 테스트 구성에만 있으며 운영 API/바이너리 경로로 우회할 수 없다.

submission_receipts는 프로필 버전 또는 최초 해금별 서버 접수 시각을 보존한다. 네 NFC 미션 evidence의 accepted_received_at은 수행/해금 조건을 모두 만족한 접수 시각의 최댓값이다. SHOW ME는 지급 직전 DB `UTC_TIMESTAMP(6)` 한 표본으로 `[opens_at, submit_closes_at)`을 검증하고 그 값을 인정·완료 시각에 함께 저장한다. 프로필 접수·자동 해금도 DB 시계를 쓰며 사전 등록 시각으로 보상을 소급하지 않는다. 처리 중 마감에 도달하면 동일 트랜잭션의 저장/해금/근거를 롤백한다. profile.version은 사용자별 값이므로 PROFILE source_id도 사용자 범위로 유일하다.

전체 완료는 정확히 SHOW_ME/SYNC_ME/MAKE_A_WISH/FAST_TAP/SAY_SOMETHING 다섯 code가 완료됐을 때만 기록한다. 사용자 PK/완료 순번 UNIQUE와 sequence 행 잠금으로 중복을 막는다. sequence 증가 후 실패도 같은 트랜잭션에서 롤백한다. completionOrder는 **운영상 처리 완료 순번**이며 awardConfirmed=false로 반환한다. 검수 지연 보정 시상 순위나 수상 확정으로 표시하지 않는다.

## 현재 API

[OpenAPI](participation.openapi.yaml)가 요청/응답 스키마를 정의한다. 전부 `/api/v1` 기준이다. GET event와 tags/pending 및 POST tags/pending을 제외한 경로는 로그인 세션이 필요하다. 모든 변경은 CSRF가 필요하다.

| Method | 경로 | 결과 |
| --- | --- | --- |
| GET | /event | NOT_CONFIGURED/BEFORE_OPEN/OPEN/CLOSED와 UTC 일정 |
| GET | /policies | 현재 두 정책 원문/버전/본인 동의 여부 |
| GET | /interests | 실제 관심사 카탈로그 |
| GET / PUT | /users/me/profile | 본인 입력/동의 상태 또는 버전 확인 후 저장 |
| GET | /users/me/photo | 본인 Mattermost 사진, no-store |
| GET | /missions, /missions/collection | 5개 미션 상태/시각, 완료 개수/전체 완료 |
| GET | /missions/completion | 전체 완료 상태/운영상 순번/시상 미확정 |
| POST | /missions/show-me/reconcile | 현재 프로필 조건 재평가, newlyCompleted 반환; 로그인·CSRF 필요 |
| POST | /tags/pending | 익명 태그 의도 저장, 202 |
| GET | /tags/pending | 임시 의도 존재 여부만 반환 |
| POST | /tags/resolve | 로그인한 사용자의 실제 태그 검증/해금 |
| POST | /tags/resume | 보관한 hash로 검증/해금, 성공 시 임시 상태 삭제 |

프론트 상태는 서버 응답으로만 갱신한다. 조회 실패를 0개 수집으로 표시하지 않으며 재시도 행동을 제공한다. 개발 미리보기는 여전히 개발 빌드 전용이고 실제 저장/글자 지급을 하지 않는다.

## 태그 발급 도구와 검증

```bash
node scripts/create-tag-bundle.mjs --origin https://YOUR_DOMAIN --out /PRIVATE/NEW_DIRECTORY
```

Git 저장소 밖의 새 디렉터리에 권한 0700으로 저장하며 기존 디렉터리를 덮어쓰지 않는다. hash만 포함한 tags.sql과 물리 NFC/QR 제작용 private-tag-urls.json(0600)을 만든다. stdout에는 원문을 출력하지 않는다. 도구는 날짜/동의/DB 연결을 만들지 않는다. 운영자가 SQL을 검토하고 백업 후 별도로 적용한다. 재발급 시 기존 태그 비활성화는 별도의 승인된 DB 작업이며 기존 해금 이력은 지우지 않는다.

자동 검증은 Testcontainers MySQL/Redis의 태그/프로필 순서, 동시 해금/완료/다중 사용자 순번, 롤백, 정책 교체/레거시 비노출, CSRF와 오래된 세션을 포함한다. 브라우저 검사는 명시적 API 대역으로 수행하며 실제 참가자/정책/행사 태그를 만들지 않는다. 실제 Mattermost 권한/DM, NFC/QR 실기기, 정책 확정과 행사 용량은 여전히 운영 전 확인 대상이다.
