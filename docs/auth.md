# 회원가입, 비밀번호 로그인과 DM 인증

이 문서는 인증의 기준 설계이며 현재 인증 기능이 구현됐다는 뜻이 아니다. **일반 로그인은 Mattermost 아이디와 TAG ME에서 설정한 비밀번호로 한다.** 숫자 6자리 DM 인증은 최초 가입과 비밀번호 변경/재설정에 사용한다. 이전의 OTP 단독 로그인 설계는 이 문서로 대체한다.

## 1. 아이디와 비밀번호의 의미

| 값 | 용도 | 저장/처리 |
| --- | --- | --- |
| Mattermost username | 사용자가 로그인에 입력하는 아이디. 별도 TAG ME 아이디를 만들지 않음 | 서버에서 조회한 정식 username을 users.mattermost_username에 보관하는 후속 설계 |
| Mattermost 내부 user ID | 외부 계정의 변경되지 않는 연결 키 | 기존 users.mattermost_id UNIQUE 유지, 공개 응답에 미포함 |
| TAG ME 비밀번호 | 최초 가입 시 사용자가 정하는 서비스 비밀번호 | 후속 user_credentials.password_hash에 단방향 해시만 저장 |
| 숫자 6자리 인증번호 | 가입/변경/재설정 시 해당 Mattermost 계정 소유 확인 | DM 전송, Redis에 목적/계정/challenge/HMAC/시도 제한/만료 저장 |
| 닉네임 | 서비스 목록 표시 | 로그인 아이디가 아님, 중복 허용 |

TAG ME 비밀번호를 Mattermost로 보내거나 Mattermost의 비밀번호를 가져오지 않는다. 화면에는 Mattermost 비밀번호를 입력하라는 안내를 넣지 않는다. 사용자에게는 "아이디는 Mattermost 아이디, 비밀번호는 가입할 때 설정한 TAG ME 비밀번호"라고 설명한다.

username은 표시 이름/실명과 다르다. 서버가 입력 username으로 Mattermost 계정을 조회하고 반환된 내부 ID로 서비스 계정을 연결한다. username 변경/재사용으로 다른 계정이 기존 자격증명을 이어받지 않도록 내부 ID를 기준으로 판단한다. 확인된 같은 내부 ID의 username 갱신만 허용하고 UNIQUE 충돌은 자동 계정 합병 없이 확인 절차로 보낸다.

## 2. 사용자 흐름

### 회원가입

`Mattermost 아이디 입력 → 지정 채널 참여 확인 → SIGN_UP 목적의 숫자 6자리 DM 발송 → 인증번호와 새 비밀번호 입력 → 계정 소유 재검증 → 회원/비밀번호 해시 저장 → 세션 생성 → 프로필/동의 → 원래 태그 복귀`

가입 완료 요청에서 challenge와 code, 새 비밀번호를 함께 받아 같은 계정/목적의 인증인지 검사한다. 인증번호만 확인해서 재사용 가능한 로그인 권한을 반환하지 않는다. 이미 가입된 내부 ID에 대해 비밀번호를 덮어쓰지 않고 로그인/비밀번호 재설정으로 안내한다. 회원과 credentials는 같은 DB 트랜잭션에서 생성한다.

### 일반 로그인

`Mattermost 아이디와 TAG ME 비밀번호 입력 → Mattermost 계정의 내부 ID 확인 → 연결된 서비스 계정/비밀번호 해시 검사 → 지정 채널/계정 상태 확인 → 세션 ID 교체와 인증 저장 → 수집판 또는 원래 태그 복귀`

일반 로그인에서는 DM 인증번호를 추가로 요구하지 않는다. 계정 없음/비밀번호 불일치는 공통 실패로 응답하고 계정/IP별 시도 제한을 둔다. 외부 조회 실패 시 계정 연결이나 범위 검증을 생략하지 않는다. 존재하지 않는 계정도 해시 검증 시간 차이로 과도한 정보를 드러내지 않도록 테스트한다.

### 로그인한 사용자의 비밀번호 변경

`내 정보 → PASSWORD_CHANGE 목적의 6자리 DM 요청 → 인증번호와 새 비밀번호 제출 → 현재 세션의 계정/challenge/purpose 확인 → 비밀번호 해시 갱신 → 모든 기존 세션 무효화 → 새 비밀번호로 다시 로그인`

변경 대상은 세션에서 정한다. 다른 username/user ID를 본문에 넣어 대상을 바꿀 수 없다. 인증번호는 같은 계정의 Mattermost DM으로 보내고 가입용/재설정용 코드를 변경에 재사용할 수 없다.

### 비밀번호를 잊은 사용자의 재설정

`로그인 화면의 비밀번호 찾기 → Mattermost 아이디 입력 → PASSWORD_RESET 목적의 6자리 DM → 인증번호와 새 비밀번호 제출 → 계정 소유/대상 범위 검증 → 해시 갱신과 기존 세션 무효화 → 로그인 화면`

재설정에는 기존 비밀번호나 기존 로그인 세션이 없어도 되지만 본인 계정의 DM 인증이 반드시 필요하다. 재설정 성공만으로 자동 로그인시키지 않는다. 미가입 계정은 재설정으로 신규 회원을 만들지 않는다.

## 3. OTP 목적과 검증

| purpose | 발송 대상 | 완료 API | 로그인 세션 필요 |
| --- | --- | --- | --- |
| SIGN_UP | 입력한 username으로 확인한 Mattermost 계정 | `/auth/signup` | 없음 |
| PASSWORD_CHANGE | 현재 로그인한 계정 | `/users/me/password` | 필요 |
| PASSWORD_RESET | 입력한 username에 연결된 기존 회원 | `/auth/password/reset` | 없음 |

OTP는 `^[0-9]{6}$` 문자열이며 앞자리 0을 허용한다. 매 발급 시 안전하게 새로 생성하고 원문 대신 HMAC을 저장한다. challenge는 외부 내부 ID, purpose, 만료, 시도 횟수와 묶는다. PASSWORD_CHANGE는 요청한 세션 사용자에도 연결한다. 계정/목적이 다른 코드, 만료/소진 코드와 재발송 이전 코드는 거절한다.

제안 기본값은 유효시간 3분, 재발송 간격 60초, challenge별 오답 5회다. 계정/IP 제한과 목적별 challenge 정책을 함께 적용한다. 과다 발송을 목적 전환으로 우회하지 못하도록 계정 전체 발송 제한도 둔다. 최종 수치는 실제 운영 검증 후 설정화한다.

최종 작업 요청에서 비밀번호 형식과 목적/세션을 먼저 검사하고 OTP 소비를 원자적으로 수행한다. OTP 소비와 MySQL 쓰기는 서로 다른 저장소라 하나의 ACID 트랜잭션이 아니다. DB/외부 오류가 나면 가입/변경을 성공으로 응답하지 않고 새 인증번호 요청을 안내한다. 같은 코드를 복구 명목으로 다시 활성화하지 않는다.

## 4. 비밀번호 저장과 세션

- Spring Security PasswordEncoder 기반의 적응형 단방향 해시와 검증을 사용한다. 알고리즘/비용과 비밀번호 최소/최대 길이는 구현 전에 확정해 클라이언트/서버/안내에 함께 적용한다. 평문이나 복호화 가능한 비밀번호 저장을 금지한다.
- 비밀번호는 요청 본문으로만 받고 URL, 애플리케이션/프록시/감사 로그, 분석 이벤트, 응답, Mattermost/AI 요청에 넣지 않는다. 입력값을 임의 trim/소문자 변환하지 않는다.
- user_credentials는 사용자당 하나이며 password_hash, password_updated_at, credential_version을 가진다. password_hash는 어떠한 프로필 DTO에도 포함하지 않는다.
- 가입/로그인 성공 시 세션 ID를 교체하고 SecurityContext를 저장한다. 세션에 credential_version을 기록한다.
- 비밀번호 변경/재설정은 해시와 version 증가를 같은 DB 트랜잭션으로 처리한다. 보호 요청마다 저장된 현재 version/계정 상태를 확인해 이전 version의 세션은 거절한다. Redis 세션 삭제도 수행하되 삭제 성공만을 접근 차단의 유일한 근거로 삼지 않는다.
- 변경/재설정 후 현재 세션을 포함해 모두 로그아웃시키고 새 비밀번호 로그인을 요구한다. 실패한 변경은 기존 비밀번호/버전을 바꾸지 않는다.
- 모든 변경 요청은 CSRF를 검사한다. 회원가입/로그인/OTP/재설정이 permitAll이어도 CSRF와 속도 제한을 생략하지 않는다.

## 5. 예정 API 계약

기본 경로는 `/api/v1`이다. 현재 구현 API에 아직 추가되지 않았다.

| Method | 경로 | 요청 | 결과 |
| --- | --- | --- | --- |
| POST | `/auth/otp/request` | purpose, username(SIGN_UP/RESET만) | challengeId, expiresIn, 재발송 대기 정보; 계정 정보 노출 최소화 |
| POST | `/auth/signup` | challengeId, code, password | 회원/credentials 생성, 세션, onboardingRequired |
| POST | `/auth/login` | username, password | 세션과 onboardingRequired |
| POST | `/auth/logout` | CSRF | 세션 무효화 |
| PUT | `/users/me/password` | challengeId, code, newPassword | 변경, 전체 기존 세션 무효화, 재로그인 안내 |
| POST | `/auth/password/reset` | challengeId, code, newPassword | 재설정, 전체 기존 세션 무효화, 재로그인 안내 |

OTP는 각 완료 API 안에서 검사/소진한다. OTP만으로 로그인 세션을 만드는 `/auth/otp/verify` 경로는 제공하지 않는다. 400은 형식/비밀번호 정책 오류, 401은 로그인 실패/유효하지 않은 인증, 403은 계정/목적/범위/CSRF에 따른 거절, 409는 계정 생성/상태 충돌, 429는 제한, 503은 일시 장애로 구분하되 응답이 계정 유무를 불필요하게 드러내지 않도록 구현한다.

## 6. 스키마와 구현 경계

현재 V1 users에는 mattermost_id만 있고 로그인 username/비밀번호 해시는 없다. 따라서 문서 수정만으로 비밀번호 로그인이 동작하지 않는다. 새 migration에서 users.mattermost_username UNIQUE와 user_credentials를 추가한다. 기존 V1/V2는 수정하지 않는다. 기존 데이터가 있다면 실제 Mattermost 조회/검증으로 username을 보충하고 임의 비밀번호나 공용 비밀번호로 채우지 않는다.

기존에 비밀번호 없는 계정이 있다면 DM 인증 후 최초 비밀번호 설정 절차를 거친다. 유효한 hash가 없는 계정은 일반 로그인을 허용하지 않는다. username 컬럼의 길이/대소문자/정렬 규칙은 실제 Mattermost API 계약을 확인해 정하며 닉네임 컬럼을 재사용하지 않는다.

## 7. 필수 검증

- 가입: 지정 채널, 동일 내부 ID 중복 가입, 올바른 purpose와 6자리, 앞자리 0, 오답/만료/재사용, 해시 저장과 가입 롤백.
- 로그인: 올바른/잘못된 비밀번호, 미가입/해시 없음, 정지/탈퇴, 외부 장애, username 변경/재사용의 계정 연결, 계정/IP 제한, 세션 ID 교체.
- 변경: 타인 대상으로 변경 불가, SIGN_UP/RESET 코드 혼용 불가, 세션 계정 바인딩, 오답/만료/동시 변경, 성공 시 현재/다른 기기 세션 무효화.
- 재설정: 기존 비밀번호 없이 본인 DM 인증 필수, 미가입 회원 생성 금지, 코드 재사용 금지, 자동 로그인 없음, 새 비밀번호만 유효.
- 공통: 로그/DTO/분석에 비밀번호/hash/OTP 누출 없음, 목적 전환 rate limit 우회 불가, 변경 전후 CSRF 재발급과 태그 복귀 유지.
