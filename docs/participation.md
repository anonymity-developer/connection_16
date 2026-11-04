# 프로필, 동의, 태그와 수집판 구현

페이즈 3은 인증된 사용자가 자신의 프로필과 동의를 저장하고, 현장 태그로 미션을 해금하며, 서버가 검증한 SHOW ME 완료를 수집판에 반영하는 범위다. 성향/소원/탭/응원의 실제 수행과 공개 동료 탐색은 다음 페이즈다. 관리자 설정 화면/배포는 아직 없다.

## 시작 전 운영 설정

V4는 행사 설정, 버전별 동의, 접수 근거와 동의 이력 테이블을 추가한다. 관심사 카테고리만 시드하며 행사 일정, 동의 원문, 참가자와 태그는 넣지 않는다. `event_settings` 행 또는 두 종류의 현재 정책이 없으면 참여 쓰기를 거절한다. 실제 Mattermost 연동의 확인 여부는 [인증](auth.md)과 별개다.

`event_settings.id=1`의 opens_at/submit_closes_at/moderation_closes_at은 UTC DATETIME(6)다. 시작 < 신규 제출 마감 <= 검수 마감을 만족해야 한다. `profile_preparation_enabled=false`가 기본값이며, true로 명시한 경우에만 시작 전 프로필 저장을 허용한다. 태그 해금과 공식 완료는 항상 행사 진행 중에만 가능하다. 종료 후에도 자신의 기록 조회와 공개 동의 철회는 가능하다.

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

## 내 프로필과 동의

`GET /users/me/profile`은 본인에게만 상세 입력값, 관심사 ID, 현재 정책의 원문/버전/동의 여부와 준비 상태를 반환한다. 닉네임은 최대 30자, 실명 100자, 소개 200자, 반은 1~9999의 정수다. 지역은 서버에서 서울로 고정한다. 관심사는 실제 카탈로그 ID 1~5개이며 중복을 거절한다. 링크는 선택이고 HTTPS만 허용한다. GitHub 링크는 github.com만 허용하며 URL의 사용자정보/fragment/비표준 포트/제어 문자를 거절한다. 외부 URL을 서버가 조회하지 않는다.

실명과 반은 **참가자의 직접 입력값**이다. Mattermost 계정 소유와 채널 참여 확인이 학적 정보까지 검증한 것은 아니다. 외부 인증으로 검증한 실명/반으로 표시하지 않으며 운영상의 별도 확인 절차는 미구현이다.

저장은 expectedVersion을 받아 같은 사용자의 다른 탭에서 변경한 값을 조용히 덮어쓰지 않는다. 신규는 0, 기존은 조회한 profile.version을 보낸다. 이전 버전은 409 PROFILE_CHANGED이며 새로 불러와야 한다. 저장/동의 이력/관심사/접수 근거/해당 글자 지급은 한 트랜잭션이다. 실패하면 함께 롤백한다.

PARTICIPATION의 현재 버전 동의가 있어야 프로필을 저장한다. 공개를 선택하면 PROFILE_PUBLICATION의 현재 버전도 필요하다. `acceptPolicies`는 사용자가 명시적으로 체크한 새 동의만 포함하며 기존 동의는 서버에서 확인한다. 모르는/오래된 버전은 409 POLICY_CHANGED로 거절한다. 새로운 정책은 기존 동의를 자동 승계하지 않는다.

`PUT /users/me/consents/{type}`에 agreed=false를 보내면 해당 종류의 활성 동의를 모든 버전에 걸쳐 철회하고 프로필을 비공개로 바꾼다. version은 화면에서 확인한 문구 버전이며 철회 시 최신 포인터 일치를 요구하지 않는다. 마감/정책 교체/행사 미설정에도 철회를 막지 않는다. 재동의는 현재 문구에만 가능하고 자동 재공개/글자 지급은 하지 않는다. 동의/철회는 user_consent_events에 누적하고 현재 상태는 user_consents에 둔다. 반복 철회로 공개 상태나 이력을 중복 변경하지 않는다.

비공개 전환은 과거 완료/접수 근거를 지우지 않는다. 최종 시상 자격과 개인정보 삭제/보관 정책은 별도 운영 범위다. 공개 프로필 목록/상세/추천/그래프 API는 이번 페이즈에 제공하지 않으며 후속 구현에서도 is_public과 현재 동의/계정 상태를 함께 검사해야 한다.

## 태그와 로그인 복귀

태그 원문은 32바이트 난수의 43자리 Base64URL이다. 서버는 길이/문자뿐 아니라 32바이트 디코딩 후 재인코딩 일치까지 검사한다. DB에는 원문 문자열의 SHA-256만 저장한다. 태그 목록/수집판/오류 응답에는 원문이나 hash를 반환하지 않는다.

1. `/t/{token}` GET은 화면만 연다. 자동 해금이나 상태 변경을 하지 않는다.
2. 미로그인 사용자가 ‘로그인하고 이어가기’를 누르면 CSRF를 포함한 POST `/tags/pending`으로 의도를 저장한다. IP 제한을 적용하고 태그 존재는 조회하지 않는다.
3. 서버 세션에는 원문 대신 hash와 15분 만료만 저장한다. 성공 응답은 202이고 본문이 없다. GET `/tags/pending`은 present만 반환한다.
4. 로그인 시 세션 ID 교체 후에도 이 임시 상태가 남는다. 브라우저 localStorage/sessionStorage에는 자격증명이나 태그를 보관하지 않는다.
5. 프로필 준비가 필요하면 내 계정/프로필부터 진행한다. 이후 고정 내부 경로 `/t/resume`에서 POST `/tags/resume`으로 이어간다. 외부 return URL은 받지 않는다.
6. 로그인한 사용자의 직접 태그는 POST `/tags/resolve`로 처리한다. 세션/CSRF/계정 버전/행사/태그/미션 활성 상태를 검증한다.
7. resolve 성공 후 새 해금 여부/글자 지급 여부와 정해진 내부 미션 경로를 반환한다. resume 성공 시 임시 의도를 지운다. 실패 시에는 다시 시도할 수 있고 15분이 지나면 다시 스캔해야 한다.

형식 오류는 400, 미존재/비활성 태그는 같은 404, 행사 외/비활성 미션은 403이다. 사용자 행 잠금과 progress 복합 PK로 중복 해금을 막는다. 첫 해금 시각/접수 ID를 덮어쓰지 않는다. URL 공유로 다른 인증 사용자가 해금할 수 있으므로 현장 체류 증명으로 안내하지 않는다. HTML meta와 운영 Caddy는 no-referrer를 사용한다. 접근 로그/분석 도구에 태그 경로를 수집하지 않는다.

## SHOW ME와 공통 완료

프로필 완성만으로는 첫 번째 S를 지급하지 않는다. 공개된 필수 프로필 + 두 현재 동의 + SHOW ME 해금이 모두 필요하다. 태그가 먼저면 프로필 저장이, 프로필이 먼저면 태그 해금이 공통 완료를 호출한다. 이후 수정/재스캔은 최초 완료 시각을 바꾸지 않는다.

MissionCompletionService는 클라이언트용 범용 완료 API가 아니다. 사용자 행 잠금 → 계정/행사 → 미션 활성/해금 → 서버 receipt 소유 → 해당 미션의 CompletionRule 검증 → 최초 evidence/progress → 다섯 code 판정 → 필요할 때만 전역 sequence 잠금 순서다. 비밀번호 버전과 ACTIVE 상태는 실제 쓰기 트랜잭션 안에서도 확인한다.

현재 운영에 등록한 CompletionRule은 SHOW_ME뿐이다. 다음 페이즈의 네 미션은 유효 결과/승인 근거를 확인하는 별도 rule을 구현해야 한다. 테스트의 가상 미래 rule은 테스트 구성에만 있으며 운영 API/바이너리 경로로 우회할 수 없다.

submission_receipts는 프로필 버전 또는 최초 해금별 서버 접수 시각을 보존한다. evidence의 accepted_received_at은 수행/해금 조건을 모두 만족한 접수 시각의 최댓값이다. 락 대기 때문에 요청 순서와 처리 순서가 달라도 이전 미해금 행위를 유효 완료 시각으로 앞당기지 않는다. profile.version은 사용자별 값이므로 PROFILE source_id도 사용자 범위로 유일하다.

전체 완료는 정확히 SHOW_ME/SYNC_ME/MAKE_A_WISH/FAST_TAP/SAY_SOMETHING 다섯 code가 완료됐을 때만 기록한다. 사용자 PK/완료 순번 UNIQUE와 sequence 행 잠금으로 중복을 막는다. sequence 증가 후 실패도 같은 트랜잭션에서 롤백한다. completionOrder는 **운영상 처리 완료 순번**이며 awardConfirmed=false로 반환한다. 검수 지연 보정 시상 순위나 수상 확정으로 표시하지 않는다.

## 현재 API

[OpenAPI](participation.openapi.yaml)가 요청/응답 스키마를 정의한다. 전부 `/api/v1` 기준이다. GET event와 tags/pending 및 POST tags/pending을 제외한 경로는 로그인 세션이 필요하다. 모든 변경은 CSRF가 필요하다.

| Method | 경로 | 결과 |
| --- | --- | --- |
| GET | /event | NOT_CONFIGURED/BEFORE_OPEN/OPEN/CLOSED와 UTC 일정 |
| GET | /policies | 현재 두 정책 원문/버전/본인 동의 여부 |
| GET | /interests | 실제 관심사 카탈로그 |
| GET / PUT | /users/me/profile | 본인 입력/동의 상태 또는 버전 확인 후 저장 |
| PUT | /users/me/consents/{type} | 동의 또는 철회, 204 |
| GET | /missions, /missions/collection | 5개 미션 상태/시각, 완료 개수/전체 완료 |
| GET | /missions/completion | 전체 완료 상태/운영상 순번/시상 미확정 |
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

자동 검증은 Testcontainers MySQL/Redis의 태그/프로필 순서, 동시 해금/완료/다중 사용자 순번, 롤백, 동의 교체/철회, CSRF와 오래된 세션을 포함한다. 브라우저 검사는 명시적 API 대역으로 수행하며 실제 참가자/정책/행사 태그를 만들지 않는다. 실제 Mattermost 권한/DM, NFC/QR 실기기, 정책 확정과 행사 용량은 여전히 운영 전 확인 대상이다.
