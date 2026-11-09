# Mattermost 실연동 점검과 계정 UI 적용

기준: `feat/network-graph` / `86ad1dd`. 로컬 작업 브랜치: `work/ssafy-auth-ui`.
코드 검토 및 UI 구현만 수행했다. 실제 MM 접속·DM·운영 DB 변경·commit/push/PR/배포는 수행하지 않았다.

## 코드 검토 결과

- `MattermostClient`: 호출별 발신 계정 로그인 → Token 헤더 사용 → 사용자/지정 채널 멤버십 확인 또는 DM 채널 생성/post → finally 로그아웃을 확인했다. 로그인 body가 잘못되어도 유효한 발급 토큰은 정리를 시도한다. PAT 모드에서는 세션 로그인/로그아웃을 하지 않는다.
- `AuthProperties`: PAT와 로그인 자격증명 중 정확히 하나, 채널 ID와 팀/채널 이름 쌍 중 정확히 하나만 허용한다. 부분/혼합 설정은 활성화 시 시작 실패한다. Compose 및 `.env.example`의 전달 변수도 일치한다. 이 범위에서 명확한 수정 필요 사항은 발견하지 않아 백엔드/Compose를 변경하지 않았다.
- 401/403/429/서버 오류와 타임아웃은 실패 처리하며 외부 본문·비밀값을 사용자에게 전달하지 않는다. 연결/읽기 제한은 각각 3초이며 DM 자동 재시도는 없다. MM 429는 TAG ME에서 일반 503으로 처리한다. TAG ME 자체 요청 제한의 429와 구분한다.
- **한계:** 외부 로그아웃 실패는 원래 결과를 덮어쓰지 않는다. 토큰을 로컬에서 보관하지 않지만 MM 서버의 세션 폐기는 보장할 수 없다. 승인된 발신 계정의 세션 화면에서 실제 정리를 별도로 확인한다. 실패했다고 DM 요청을 반복하지 말고 먼저 수신 여부와 서버 제한을 확인한다.

## UI

기존 컴포넌트/폰트를 사용해 인증·프로필·태그 안내 경로에만 블루/화이트 토큰을 적용했다. 공식 SSAFY 로고/확정 색상 자료는 저장소에 없어 새 로고를 만들지 않았다. 새 색상은 TAG ME 행사 UI 팔레트이며 공식 브랜드 값으로 표시하지 않는다.

로그인 카드, 계정 입력 → DM 인증/비밀번호 영역, 분:초 타이머, 서버 오류 알림, 읽을 수 있는 비활성 버튼을 추가했다. 기존 두 단계 인증 계약은 유지하며 OTP를 별도 검증 완료로 표시하지 않는다. SHOW ME 체크리스트의 소개/관심사는 편집 중 입력 상태이고 태그와 지급 완료는 서버 수집판 상태다. 프로필 필수/선택·공개 동의·NFC 복귀·4시간 세션·지급 조건은 유지했다. 관심사/한 줄 소개 미리보기도 제공한다.

디자인 검토 기록: framed-sheet, seed 1340789336, 새 방향 재추첨 없음. 기존 내비게이션을 보존하고 파란 배경 위 흰 입력 카드로 적용했다. 질문은 '어떤 계정으로 참여하나?'와 '첫 번째 S에 무엇이 남았나?'다. 전체 운영 화면 렌더는 아래 환경 제약으로 **미검증**이다.

## 이 환경에서 실행한 검사

| 검사 | 결과 |
| --- | --- |
| `npm run lint` | PASS |
| `npm run build` | PASS |
| `npm run test:unit` | PASS, 13개 |
| `node scripts/verify-skills.mjs` | PASS |
| 변경 UI 정적 디자인 검사 | 0 FAIL / 1 WARN, 타이머의 `padStart` 경고는 실제 시계 표현이므로 유지 |
| `git diff --check` | PASS |
| MM 단위 테스트 | 미실행: Gradle 배포본 다운로드 Network is unreachable, 환경 JDK 17이며 JDK 21 없음 |
| 인증/NFC 브라우저 계약 | 미실행: Chromium 실행 파일 없음. 다운로드도 손상된 zip 오류로 실패 |
| 프로필 브라우저 계약/360·390·430·768·1440 화면·대비/포커스 실측 | 미실행: 동일한 브라우저 제약. 기존 프로필 계약에 편집 상태가 지급 완료로 보이지 않는 검사만 추가 |
| MySQL/Redis 통합 검사 | 미실행: Docker 없음. 다른 DB로 대체하지 않음 |
| 실제 MM·모바일 키보드·NFC 실물 | 미실행 |

이전 세션/사용자 환경의 성공 기록을 이번 실행 결과로 계산하지 않았다.

## Windows PowerShell 적용

ZIP을 다운로드해 별도 폴더에 풀고 `tagme-ssafy-auth-ui.patch` 경로를 아래에 넣는다. 패치는 기존 파일에 덮어쓰기보다 먼저 충돌을 검사한다. **현재 작업 파일이 있으면 먼저 본인이 보관하고 진행한다.** 최신 브랜치가 기준 커밋 이후 변경됐다면 강제 적용하지 말고 diff를 비교한다.

```powershell
Set-Location 'C:\Users\amy\SSAFY_connection16\connection_16'
git status --short
git rev-parse --short HEAD
# status가 비어 있고 기준이 맞을 때 진행. 기존 동명 브랜치가 있으면 다른 이름 사용.
git switch -c work/ssafy-auth-ui
$patch = "$HOME\Downloads\TAGME_ssafy_auth_ui_86ad1dd\tagme-ssafy-auth-ui.patch"
git apply --check $patch
# 위 명령이 성공했을 때만 실행
git apply $patch
git diff --check
git diff --stat
```

파일 추가를 포함한 일반 diff 패치다. commit이나 push는 하지 않는다. ZIP의 `changed-files/`는 검토용이며 자동 복사하지 않는다.

```powershell
Set-Location frontend
npm ci
npm run lint
npm run build
npm run test:unit
# 브라우저가 아직 없을 때만
npx playwright install chromium
npm run test:auth-ui
npm run test:auth-nfc-ui
npm run test:participation-ui
Set-Location ..\backend
# JDK 21 / Docker Desktop 실행 상태에서
.\gradlew.bat test --tests 'com.tagme.auth.Mattermost*Test' --tests 'com.tagme.auth.AuthPropertiesBindingTest'
.\gradlew.bat integrationTest --tests 'com.tagme.integration.AuthIntegrationTest' --tests 'com.tagme.integration.ParticipationIntegrationTest'
Set-Location ..
```

두 통합 테스트 클래스는 기준 커밋에 존재한다. 실제 MySQL/Redis는 Docker Desktop으로 실행하며 MM 자체는 mock이다.

## 실제 MM: 사용자 본인에게만 한 번 검증

1. 승인된 **발신 계정**과 본인 **수신 계정**이 서로 다른 계정인지 확인한다. 구현은 발신자 자신에게 보내는 DM을 거절한다. 팀/채널은 화면 표시명이 아닌 URL의 이름 또는 확인된 26자리 채널 ID를 사용한다. 발신 계정에서 본인 조회·지정 채널 멤버 조회·DM 생성·post 작성 권한이 필요하다. MFA/SSO 전용 등 서버 정책이 비밀번호 API 로그인을 막으면 우회하지 않고 승인된 PAT 방식 가능 여부를 확인한다.
2. 실제 `.env`는 본인 노트북의 편집기에서만 설정한다. 기존 파일을 교체하지 않는다. `AUTH_ENABLED=true`, `MATTERMOST_BASE_URL`, `AUTH_HMAC_KEY`를 설정하고, 발신 방식과 채널 방식은 아래 표에서 하나씩 선택한다. 사용하지 않는 쪽은 비운다. 비밀번호는 Compose `.env` 문법에 맞게 작은따옴표로 감싸 `$` 등 특수문자가 원래 값으로 전달되게 한다. 값에 작은따옴표가 있으면 Compose의 이스케이프 규칙에 맞춘다. 자격증명은 대화/명령행/스크린샷에 붙이지 않는다.

| 선택 | 채울 변수 | 비워둘 변수 |
| --- | --- | --- |
| 발신 계정 로그인 | `MATTERMOST_LOGIN_ID`, `MATTERMOST_LOGIN_PASSWORD` | `MATTERMOST_TOKEN` |
| PAT 대안 | `MATTERMOST_TOKEN` | `MATTERMOST_LOGIN_ID`, `MATTERMOST_LOGIN_PASSWORD` |
| 채널 ID | `MATTERMOST_CHANNEL_ID` | `MATTERMOST_TEAM_NAME`, `MATTERMOST_CHANNEL_NAME` |
| 팀/채널 이름 | `MATTERMOST_TEAM_NAME`, `MATTERMOST_CHANNEL_NAME` | `MATTERMOST_CHANNEL_ID` |

`.env`가 없는 경우에만 복사한다. HMAC 키는 로컬 비밀값 생성기로 최소 32바이트 무작위 Base64를 만들어 편집기에 입력한다. 기존 키가 있으면 불필요하게 바꾸지 않는다.

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
# 설정 완료 후 로컬 개발 스택만 기동
docker compose -f compose.yml -f compose.dev.yml --profile app up -d --build
docker compose -f compose.yml -f compose.dev.yml --profile app ps
Start-Process 'http://localhost:8088/auth/signup'
```

3. 회원가입에 **본인의 Mattermost username만** 입력해 인증번호를 한 번 요청한다. 이 요청 안에서 발신 계정 로그인, 본인 조회, 지정 채널 멤버십 확인, 본인 DM 생성/전송, 로그아웃이 실행된다. OTP 요청의 200 응답은 계정 유무 보호 때문에 DM 발송 성공을 보장하지 않는다. **실제 DM 수신**을 확인한다. 발신 계정 화면에서도 새 임시 세션이 남는지 확인하고 비밀값/원문을 공유하지 않는다. 이미 TAG ME에 가입했다면 회원가입 DM 대신 본인 비밀번호 재설정 흐름을 사용한다.
4. 수신한 6자리를 앞자리 0까지 입력하고, MM 비밀번호와 별개인 TAG ME 비밀번호를 15~64자로 설정한다. 가입 성공 후 프로필/동의는 기존 승인된 행사 설정이 준비됐을 때 진행한다. 미설정이면 프로필 저장/미션 수행이 차단되는 것이 정상이며 이 검증을 위해 정책/DB를 임의 시드하지 않는다.
5. 로그아웃 후 같은 MM 아이디 + **TAG ME 비밀번호**로 로그인한다. 이번에는 DM 없이 로그인되는지 확인한다. NFC는 같은 브라우저에서 태그 → 로그인/가입 → 프로필 → 태그 미션 복귀를 확인한다. 태그 연결은 15분, 인증 세션 유휴 만료는 4시간으로 서로 다르다. 연결이 만료되면 현장 태그를 다시 읽는다.
6. 403/503/DM 미수신이면 반복 클릭하지 말고 발신 방식·채널 식별자·권한·서버 제한을 확인한다. `/auth/config`의 available 또는 컨테이너 Healthy는 실제 MM 권한 확인의 증거가 아니다. 점검 후 테스트 목적의 인증 활성화 여부는 사용자가 관리한다.

브라우저 검증은 모의 API 계약이며 실제 MM 성공과 별개다. 실제 모바일에서는 360/390/430 폭과 숫자 키패드·OTP 붙여넣기·포커스·가로 스크롤·재인증/NFC 복귀를 확인한다.
