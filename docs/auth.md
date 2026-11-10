# 회원가입, 비밀번호 로그인과 DM 인증

Mattermost 인증은 참가자 계정 기준이다. 사전 등록 운영자는 별도의 `admin:` 아이디로 같은 로그인 창에서 인증하며 Mattermost를 호출하지 않는다. V10 계정 등록/접속 시간/권한 계약은 [관리자 콘솔](admin-console.md)을 따른다.

페이즈 2의 서버 API와 프론트 연결을 구현했다. **일반 로그인은 Mattermost 아이디와 TAG ME 비밀번호**를 사용한다. 가입/비밀번호 변경/분실 재설정에만 숫자 6자리 DM 인증을 사용한다. 실제 Mattermost 토큰의 지정 채널 조회·DM 권한과 실제 발송은 아직 검증하지 않았다. 기본 `AUTH_ENABLED=false`이며 자동 테스트의 외부 응답은 테스트용 대역이다.

## 1. 계정과 비밀번호

| 값 | 구현 |
| --- | --- |
| Mattermost username | 입력 아이디. 서버에서 trim/소문자 변환 후 조회하며, 반환된 정식 username을 저장 |
| Mattermost 내부 ID | 외부 계정 연결 기준. username 변경/재사용으로 다른 계정의 비밀번호를 이어받지 않음 |
| TAG ME 비밀번호 | 가입 시 설정. 원문을 trim/소문자 변환하지 않고 해시 검증 |
| 6자리 DM 코드 | `^[0-9]{6}$` 문자열. 앞자리 0을 유지하고 매번 SecureRandom으로 새로 생성 |
| challenge ID | 32바이트 무작위 Base64URL 43자리. 사용자가 입력하는 6자리 번호와 별개 |
| 닉네임 | 프로필 단계에서 입력할 별도 값. 아이디로 사용하지 않음 |

V3가 `users.mattermost_username`과 `user_credentials`를 추가한다. username은 최대 64자, `utf8mb4_0900_bin` UNIQUE다. V9부터 가입 인증 성공 시 real_name/region/class_no를 Mattermost 이름에서 파싱해 저장한다. 파싱 실패 시 가입을 보류하며 가짜 이름/지역/반으로 채우지 않는다. [가져오기 계약](mattermost-profile.md)을 따른다. 기존 행의 username은 실제 조회 전까지 NULL, 기존 비밀번호는 자동 생성하지 않는다.

초기 비밀번호 기준은 **공백을 포함해 15~64 Unicode code point**다. 공백만 있는 값은 거절한다. 원문을 변환하지 않고, 한글/이모지도 허용한다. 서버와 프론트에 같은 길이 기준을 적용했다. Spring Security PasswordEncoder로 PBKDF2-HMAC-SHA256, 600,000회, 사용자별 16바이트 salt, 256-bit 결과를 사용한다. 인코딩 ID는 `{pbkdf2-sha256-v1}`이며 나중에 비용/알고리즘 변경을 구분할 수 있다. JDK가 제공하는 알고리즘을 사용하며 FIPS 인증을 받았다는 의미가 아니다. 실제 운영 서버의 해시 비용·가입/로그인 부하를 확인한 뒤 조정한다.

비밀번호·OTP는 URL, localStorage/sessionStorage, 분석 이벤트, 공개 DTO와 로그에 넣지 않는다. Mattermost에는 해당 목적의 인증번호만 보내며 TAG ME 비밀번호를 보내지 않는다. 요청 DTO의 문자열 표현도 민감 입력을 숨긴다.

## 2. 실제 요청 흐름

### 가입

1. `/csrf`로 쿠키/헤더 토큰을 받는다.
2. SIGN_UP과 username으로 `/auth/otp/request`를 호출한다.
3. 서버가 Mattermost 내부 ID와 ACTIVE 상태, 비봇 사용자, 지정 채널 멤버십을 확인한다.
4. 발송 가능한 신규/기존 무자격증명 계정이면 목적별 코드를 저장하고 해당 계정 DM으로 보낸다.
5. `/auth/signup`에 challengeId/code/password를 함께 제출한다.
6. 비밀번호 형식, purpose/바인딩, 원자적 OTP 소비, 외부 계정 재검증을 거친다.
7. 가져온 이름/지역/반/사진 버전, 회원과 credentials를 같은 DB 트랜잭션으로 저장한 뒤 세션 ID를 바꾸고 인증을 저장한다.
8. 가입 후 onboardingRequired이면 `/me/profile`로 이동해 닉네임(필수), 관심사/소개/링크(선택)와 참여 안내를 확인한다. 프로필/동의 등록은 [참여 계약](participation.md)에 구현했다.

이미 비밀번호가 있는 계정은 SIGN_UP으로 덮어쓰지 않는다. V1의 기존 무자격증명 계정은 같은 외부 ID에 대한 DM 확인 후 최초 비밀번호를 설정할 수 있다. 동명이거나 username이 충돌해도 자동 합병하지 않는다.

### 일반 로그인

username → Mattermost 내부 ID/채널 확인 → 그 내부 ID에 연결된 해시 검증 → 사용자 행 잠금 후 ACTIVE/version 재확인 → Mattermost 프로필과 username/last_login_at 갱신 → 세션 ID 교체와 SecurityContext 저장 순서다. 비밀번호 검증의 비싼 계산과 외부 통신은 DB 행 잠금 밖에서 수행한다. 인증 저장소는 JdbcTemplate과 기존 Spring 트랜잭션 관리자를 사용하며, 기존 사용자 작업에서는 트랜잭션의 첫 읽기로 사용자 행을 잠근다. 무자격증명 계정의 최초 설정도 challenge의 사용자 ID를 기준으로 먼저 잠가 상태를 재확인한다. 검증 중 비밀번호 버전이 달라지면 로그인하지 않는다.

계정 없음/해시 없음/정지/잘못된 비밀번호는 공통 LOGIN_FAILED다. 해시가 없는 경우도 임시 dummy 해시에 대해 검증한다. 외부 서버 오류나 권한 부족을 계정 확인 성공으로 바꾸지 않는다. 동일 내부 ID의 username 변경만 갱신한다. username이 재사용되면 새 내부 ID의 계정을 조회하므로 기존 소유자의 해시를 사용하지 않는다.

### 로그인한 사용자의 변경

PASSWORD_CHANGE는 대상 username을 받지 않는다. 현재 세션 사용자, credential_version, 세션 ID의 HMAC에 묶어 코드를 발급한다. 다른 계정/다른 세션/다른 purpose로 완료할 수 없다. 새 비밀번호 저장과 version 증가를 같은 트랜잭션에서 수행하고, 모든 이전 세션을 차단한 뒤 현재 세션도 종료한다.

### 분실 재설정

PASSWORD_RESET은 입력 username에 연결된 기존 ACTIVE 계정의 DM 소유 확인이 필요하다. 기존 비밀번호나 로그인 세션은 필요하지 않다. 미가입 계정을 만들지 않고, 재설정 후 자동 로그인하지 않는다. 현재/다른 기기의 이전 세션은 모두 거절하며 새 비밀번호로 로그인한다.

## 3. OTP와 제한

Redis Hash에는 purpose, 내부 외부 계정 ID, 대상 서비스 사용자 ID, 발급 시 credential_version, 세션 바인딩, HMAC proof와 오답 횟수를 저장한다. 원문 코드는 저장하지 않는다. Redis key의 username/IP도 HMAC으로 식별한다. 단일 Redis 인스턴스에서 Lua로 발급/재발급/소비를 원자적으로 실행한다. Redis Cluster는 현재 구성 범위가 아니며 다중 key Lua의 slot 설계를 별도로 해야 한다.

| 기본값 | 현재 값 |
| --- | --- |
| 만료 | 180초 |
| 재발송 | 60초 간격; 계정 전체 cooldown으로 purpose 전환 우회도 방지 |
| challenge 오답 | 5회 후 폐기 |
| 발송 | username/외부 내부 ID 기준 각각 15분당 5회, purpose 공통 |
| 로그인 | username 기준 15분당 10회 |
| IP | OTP 요청/검증/로그인별 분당 120회 |

잘못된 코드는 외부 통신 전에 원자적으로 검사/횟수 증가한다. 성공한 proof는 즉시 소비한다. 외부 재검증이나 DB 쓰기가 나중에 실패해도 되살리지 않는다. DB 실패는 사용자 생성/비밀번호 변경을 롤백하고 AUTH_RESTART_REQUIRED로 새 코드 요청을 안내한다. 재발송하면 이전 같은 목적의 challenge를 폐기한다. 변경/재설정은 발급 시 version을 검사하므로 다른 비밀번호 작업이 먼저 끝났다면 오래된 코드로 덮어쓰지 못한다.

OTP 요청은 미존재/미가입/기존 가입/비적격 계정에 같은 형태의 challenge 응답을 반환한다. 실제 발송 대상이 아니면 사용할 수 없는 불투명 ID만 반환한다. 전송이 가능한 경우에만 DM을 보냈다는 식별자는 노출하지 않는다. 발송 성공/계정 존재의 완전한 네트워크 시간 동일성을 보장한다는 뜻은 아니다. 재발송 제한은 외부 조회 전에도 적용한다. 실제 발송 API가 실패하면 저장한 challenge를 폐기하고 503을 반환한다. 발송 완료 후 expiresIn은 Redis에 남은 TTL을 다시 읽어 반환하므로 외부 통신에 쓴 시간을 새 유효시간처럼 더하지 않는다.

제한 값은 `tagme.auth.*` 설정과 환경변수로 조정한다. 캠퍼스 공유 NAT에서 IP가 겹칠 수 있으므로 현장 사용자 수와 해시 비용을 측정해 IP 한도를 조정한다. 클라이언트가 임의로 보낸 X-Forwarded-For를 인증 코드에서 직접 읽지 않는다. 운영은 외부에 API를 노출하지 않고 Caddy가 정리한 전달 헤더를 기존 Spring proxy 설정으로 처리한다.

## 4. 세션과 요청 보안

- 모든 변경 API에 기존 CSRF 쿠키/헤더 검사를 유지한다. 익명 로그인/가입/OTP/재설정도 예외가 아니다.
- 가입/로그인 시 SessionAuthenticationStrategy로 세션 ID와 CSRF를 갱신하고 HttpSessionSecurityContextRepository에 명시적으로 저장한다.
- Redis indexed session을 사용한다. `spring.session.timeout=4h`로 마지막 세션 접근 이후 4시간 동안 유효하다. 요청 시 유효 세션의 유휴 만료 시각이 갱신되며 영구 자동 로그인은 아니다. 브라우저 세션 쿠키이므로 브라우저 종료·재기동 시 보존 여부는 실기기 검증이 필요하다. principal은 서비스 사용자 ID/version/역할만 포함하며 해시/OTP를 포함하지 않는다.
- `/api/v1/admin/**`는 서버 ADMIN 역할을 요구한다. 검수와 관리자 콘솔의 현황·회원·순위·미리보기 읽기 API를 제공한다.

- 인증 요청마다 DB의 ACTIVE/version/역할을 검사한다. 변경/재설정 후 오래된 세션의 version은 거절한다. Redis 세션 삭제가 누락돼도 이 DB 검사가 권한을 막는다.
- Redis에서 이전 버전의 indexed session도 삭제한다. 새 버전으로 로그인한 세션을 무조건 같이 삭제하지 않는다.
- DB/Redis 조회 장애는 확인을 생략하지 않고 실패한다. API 응답은 no-store다.
- 운영 쿠키는 기존 HTTPS/Secure/HttpOnly/SameSite=Lax 설정을 따른다. 로그인/로그아웃/변경 후 프론트는 CSRF를 다시 받는다.
- 페이즈 3에서 프로필/공개 동의/태그 해금을 연결했다. 인증 세션이 미션 수행 권한이나 공개 동의를 의미하지 않는다.

## 5. 구현 API

기본 경로는 `/api/v1`이며 [OpenAPI](auth.openapi.yaml)에 요청/응답 스키마가 있다.

| Method | 경로 | 요청 / 응답 |
| --- | --- | --- |
| GET | `/auth/config` | available(설정상 활성 여부), 비밀번호 최소/최대 길이. 실제 외부 접속 성공을 뜻하지 않음 |
| GET | `/csrf` | headerName/token과 XSRF-TOKEN 쿠키 |
| POST | `/auth/otp/request` | purpose, username(SIGN_UP/RESET만) → challengeId/expiresIn/resendAfter |
| POST | `/auth/signup` | challengeId/code/password → 201, 내 계정 + 세션 |
| POST | `/auth/login` | username/password → 내 계정 + 세션; OTP 없음 |
| POST | `/auth/logout` | 세션 + CSRF → 204 |
| GET | `/users/me` | 내 계정 ID/username/nickname/onboardingRequired; 가져오기 완료·닉네임·현재 두 안내 확인 반영, 외부 내부 ID/hash 미포함 |
| PUT | `/users/me/password` | challengeId/code/newPassword → 204, 기존 세션 차단 |
| POST | `/auth/password/reset` | challengeId/code/newPassword → 204, 자동 로그인 없음 |

별도의 OTP 검증 성공을 재사용 가능한 로그인 권한으로 반환하는 경로는 없다. PASSWORD_CHANGE의 발송 대상과 변경 대상은 서버의 세션으로 정한다. DTO에 없는 userId 등 추가 필드는 400으로 거절한다.

| 상태 | 의미 |
| --- | --- |
| 400 | 입력/목적/비밀번호 형식 오류 |
| 401 | 일반 로그인 실패, 유효하지 않은 challenge, 만료된 세션 |
| 403 | CSRF/인증 권한 거절 |
| 409 | 외부 ID/username/이미 생성된 credentials 충돌 |
| 429 | 제한, Retry-After 초 단위 |
| 503 | 비활성/외부/저장소 오류, 재발급이 필요한 처리 실패 |

## 6. 외부 연동을 켜기 전

[개발 가이드](development.md)의 환경변수와 실행 방법을 따른다. `AUTH_ENABLED=true`에는 서버 전용 MATTERMOST_TOKEN, MATTERMOST_CHANNEL_ID, AUTH_HMAC_KEY가 필요하다. HMAC 키는 최소 32바이트 무작위 값을 Base64로 전달한다. 실제 값은 Git/브라우저/VITE 변수/PR/대화에 붙여넣지 않는다.

운영자가 확인할 권한은 사용자 username/내부 ID 조회, 지정 채널 멤버 조회, 토큰 소유자와 대상 사이의 DM 채널 생성, 그 채널 post 작성이다. 실제 환경에서 탈퇴/비참여/정지, DM 제한과 발송 실패도 확인한다. 외부 토큰의 관리 권한을 불필요하게 늘리거나 개인 비밀번호를 자동 전달하는 방식으로 우회하지 않는다. 기본 비활성 컨테이너 readiness와 실제 인증 발송 성공은 서로 다른 점검이다.

## 7. 검증 범위

- 백엔드 단위: PBKDF2 salt/한글/원문 유지, 비밀번호 길이, 민감 DTO, Mattermost 경로/멤버십/오류/DM 요청.
- Testcontainers MySQL/Redis: V3, 선행 0, 해시/세션 교체, username 재사용/변경, legacy 초기 설정, DB 롤백과 code 재사용 금지, 동일/다른 purpose·세션, 재발송/만료/오답, 동시 소비와 비밀번호 버전 경합, 현재/다른 세션 차단, 외부 실패와 CSRF.
- 브라우저: 기존 5개 폭/키보드 검사와 별도 API 계약 대역으로 signup/login/change/reset/logout, CSRF 헤더, 발송 중 중복 방지, 재발송/정책/실패 상태, 새로고침 후 서버 세션 복원.
- 실제 Mattermost, 실제 휴대전화 키보드/NFC/QR, 서버 배포/부하 검증은 아직 수행하지 않았다. 브라우저 계약 대역은 실제 외부 연동 성공의 증거가 아니다.

## 참고한 공식 계약

- [Mattermost REST API](https://docs.mattermost.com/api/reference/mattermost-api)
- [채널 멤버 조회](https://docs.mattermost.com/api/reference/get-channel-member)
- [Mattermost 공식 API YAML](https://github.com/mattermost/mattermost/blob/master/api/v4/source/channels.yaml)
- [Spring Security 6.5 세션 관리](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)
- [OWASP 비밀번호 저장](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)


## 2026-11-06 — v5 STEP 1 검증

- 기존 Mattermost 토큰/지정 채널 검증, OTP, Redis indexed session, 로그인 시 ID 교체와 CSRF, 15분 hash-only 태그 보관을 재사용했다. 가입자는 기존 프로필·동의 단계를 마친 뒤 `/t/resume`에서 원래 미션을 연다. 세션 4시간과 태그 보관 15분은 별도 제한이다.
- 참고용 발신 계정 로그인 패치는 README와 실제 diff를 비교했으나 적용하지 않았다. 호출별 외부 로그인/로그아웃과 발신 비밀번호 관리가 추가되며 실제 허용 정책이 미확정이다. PAT와 대체 방식의 최종 운영 선택은 승인 후 권한·부하 검증이 필요하다.
- `AuthSessionLifecycleTest`: 실제 Security/Session 필터와 **메모리 저장소 대역**으로 다섯 태그 × 가입/로그인, ID 교체 후 intent 보존·소비, 4시간 유휴 만료와 접근 갱신, 재로그인·기기별 로그아웃, 비밀번호 버전 변경 시 두 기기 거절을 검사한다. 실제 Redis 검증을 대체하지 않는다.
- `AuthIntegrationTest`에 실제 Redis 4시간 설정·두 세션·로그아웃 삭제·만료·재로그인 검사를 추가했다. Docker가 있는 환경에서 `cd backend && ./gradlew integrationTest --tests 'com.tagme.integration.AuthIntegrationTest'`로 실행한다. Mattermost는 이 테스트에서도 mock이다. 2026-11-06 사용자 Windows/JDK 21/Docker 환경의 BUILD SUCCESSFUL 로그로 이 통합 대상의 통과를 확인했다. 에이전트 환경은 Docker 부재로 실행이 막혔다.
- `cd frontend && npm run test:auth-nfc-ui`: 운영 빌드에 테스트 전용 API 대역을 연결해 다섯 NFC × 가입/로그인 복귀, 온보딩/새로고침, 유효 세션 재접속, 401 후 재인증·복귀, 태그 보관 만료, 로그아웃을 검사한다. 미션 본문 수행은 STEP 4 범위로 검사하지 않는다.
- 실제 Mattermost, HTTPS 쿠키·모바일 브라우저 재시작, NFC 실물은 미검증이다. 운영 Compose의 `APP_SESSION_SECURE=true`는 유지했다. 운영 DB·실 DM·배포는 수행하지 않았다.

## 2026-11-06 — 후속: Mattermost 발신 계정 세션 인증 적용

- 앞선 STEP 1의 '발신 계정 로그인 패치 미적용'은 당시 검토 시점의 기록이다. 이후 별도 작업에서 Mattermost 발신 계정 로그인·임시 세션·로그아웃 처리를 적용했다.
- 환경변수 MATTERMOST_LOGIN_ID, MATTERMOST_LOGIN_PASSWORD, MATTERMOST_TEAM_NAME, MATTERMOST_CHANNEL_NAME 설정을 추가하고 기존 토큰 방식과의 호환성을 유지했다.
- 인증 단위 테스트, 실제 Redis 인증 통합 테스트, SHOW ME 통합 테스트를 Windows/JDK 21/Docker 환경에서 강제 재실행하여 통과했다.
- 원격의 4시간 세션 및 NFC 인증 복귀 변경도 병합했다. NFC 브라우저 계약 14개, 프론트 lint와 운영 빌드가 통과했다.
- 실제 Mattermost 계정 로그인·채널 권한·DM 전송, 모바일 브라우저 및 NFC 실물 검증은 아직 수행하지 않았다.
