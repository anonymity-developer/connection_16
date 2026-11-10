# PowerShell 실행 순서 — 현재 계정 보존

## 0. 변경 검토/적용

ZIP을 Downloads에 압축 해제한다. 기존 1~5차 미커밋 UI는 그대로 유지한다. 이 패치는 새 도구·문서·테스트와 MM 테스트 메서드 1개만 추가한다. 5차 후속 UI는 포함하지 않는다.

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$bundle = Join-Path $HOME 'Downloads\TAGME_operational_readiness_86ad1dd'
$patch = Join-Path $bundle 'operational-readiness.patch'
Set-Location $repo
git status --short
git apply --check $patch
if ($LASTEXITCODE -ne 0) { throw '충돌 확인 필요. 적용 중단.' }
# 기존 MM 테스트 파일만 작업 폴더 밖으로 백업. .env는 읽거나 복사하지 않음.
$backup = Join-Path (Split-Path $repo) ('tagme-readiness-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
Copy-Item '.\backend\src\test\java\com\tagme\auth\MattermostSenderSessionTest.java' $backup -ErrorAction Stop
git apply $patch
if ($LASTEXITCODE -ne 0) { throw '적용 실패. 상태 확인 필요.' }
git diff --check
node --test scripts/readiness.test.mjs
```

## 1. 안전한 공개 로컬 endpoint 조회 (GET만)

```powershell
# 기본 API 포트가 다르면 실제 로컬 API 포트로 바꿈. IP는 127.0.0.1만 허용.
node scripts/readiness/audit.mjs --probe http://127.0.0.1:8080
```

이 도구는 event/auth-config/health만 조회하며 로그인·OTP·회원가입·세션 쿠키 전달을 하지 않는다. 응답 원문 대신 상태와 available만 출력한다. `auth/config available=true`는 설정 활성화이며 실제 MM 통신 성공 증명이 아니다. `NOT_CONFIGURED`와 `health=UP`은 동시에 가능하다. 이 Work 환경에서는 사용자 endpoint에 접속하지 않았다.

## 2. 실제 로컬 DB 읽기 전용 점검

먼저 정상 동작 중인 Compose 프로젝트의 mysql 서비스인지 확인한다. **down -v / DB 초기화 / DELETE / Redis FLUSH 금지**.

```powershell
Set-Location $repo
docker compose ps
docker compose cp .\scripts\readiness\read-only.sql mysql:/tmp/tagme-read-only.sql
if ($LASTEXITCODE -ne 0) { throw 'SQL 파일 복사 실패' }
$dbUser = Read-Host '현재 로컬 DB 사용자명 (기본 tagme)'
$dbName = Read-Host '현재 로컬 DB 이름 (기본 tagme)'
docker compose exec mysql mysql --user=$dbUser --password --database=$dbName
```

MySQL 자체 비밀번호 프롬프트에 **비공개로** 입력한다. `-p비밀번호`, `echo`, 셸 변수 출력, `.env` 내용 공유는 하지 않는다. MySQL 프롬프트에서:

```sql
source /tmp/tagme-read-only.sql;
exit;
```

출력은 3개 설정 테이블의 컬럼 정의와 설정 요약이다. 사용자/비밀번호/정책 본문/태그 토큰은 조회하지 않는다. `START TRANSACTION READ ONLY`와 `ROLLBACK`으로 끝난다. 테이블이 없으면 해당 마이그레이션 적용 상태를 따로 확인하고 이 도구로 마이그레이션하지 않는다.

`readiness_snapshot`의 JSON 객체만 새 `readiness-report.json`에 복사해 저장한 뒤:

```powershell
node scripts/readiness/audit.mjs --snapshot .\readiness-report.json
# exit 0: 자동 확인 항목 통과. exit 2: 누락/마감 등 확인 필요. exit 1: 파일/연결/형식 오류.
```

JSON에는 사용자 정보가 없지만 점검 파일을 굳이 Git에 추가하지 않는다. 보고서는 문구 승인·SYNC JSON 유효성·권한·실제 서버 흐름을 대신하지 않는다.

## 3. 개발용 설정 SQL 생성만 (현재 DB에 실행하지 않음)

```powershell
$opens = Read-Host '폐기 가능한 개발 테스트 시작 UTC (YYYY-MM-DDTHH:mm:ssZ)'
$closes = Read-Host '개발 테스트 제출 마감 UTC'
$moderation = Read-Host '개발 테스트 검수 마감 UTC'
node scripts/readiness/render-dev-sql.mjs $opens $closes $moderation |
  Set-Content -Encoding utf8 (Join-Path $backup 'dev-settings-review.sql')
if ($LASTEXITCODE -ne 0) { throw '일정 형식/순서 오류. SQL 실행 금지.' }
Get-Content (Join-Path $backup 'dev-settings-review.sql')
```

생성기는 DB에 연결하지 않는다. `tagme_local_validation`이라는 **별도 빈 스키마**·users=0·기존 설정=0 guard가 있으며, 마지막이 ROLLBACK이므로 그대로 실행해도 영속 설정되지 않는다. `LOCAL TEST ONLY`는 가짜 테스트 문구이며 실제 참여자에게 표시할 약관이 아니다. 기존 테스트 계정 DB의 막힘을 푸는 용도로 사용하지 않는다.

영속 개발 스택이 꼭 필요하면 별도 폐기 가능한 MySQL·Redis와 다른 포트/볼륨을 마련하고 운영자가 명시적으로 검토해야 한다. 현재 환경의 DB를 비우거나 복제하지 않는다. 단순 서버 검증에는 다음 Testcontainers 경로가 우선이다.
실제 행사 DB 설정은 일정·정책 승인 후 별도 변경 승인과 백업/검증 계획이 필요하다. 이 산출물은 운영 설정을 자동 적용하지 않는다.

## 4. MySQL/Redis + 모의 MM 통합 테스트 (별도 임시 컨테이너)

JDK 21과 Docker Desktop을 준비한다. 기존 Compose는 그대로 두어도 된다. 테스트는 Compose DB로 연결하지 않고 각 클래스의 fresh Testcontainers URL과 Redis 포트를 바인딩한다. 실제 MM은 mock이며, 기존 테스트 안의 정리 SQL은 **그 임시 DB에서만** 사용된다.

```powershell
javac -version
# 21이어야 함. JDK 경로/계정 비밀정보를 출력할 필요 없음.
.\scripts\readiness\Run-IsolatedTests.ps1
```

실행 정책 때문에 스크립트가 차단되면 전역 실행 정책을 바꾸지 말고 파일을 검토한 뒤 다음 고정 명령을 직접 실행한다.

```powershell
Push-Location .\backend
.\gradlew.bat --no-daemon test --tests 'com.tagme.auth.MattermostSenderSessionTest' --tests 'com.tagme.auth.AuthPropertiesBindingTest' --tests 'com.tagme.participation.OperationalReadinessTest'
if ($LASTEXITCODE -ne 0) { Pop-Location; throw '단위 테스트 실패' }
.\gradlew.bat --no-daemon integrationTest
Pop-Location
```

이 스크립트는 비밀번호나 토큰을 출력하지 않고 `.env`를 직접 읽지 않는다. 현재 DB를 테스트 DB로 넘기는 Spring datasource override를 추가하지 않는다. TESTCONTAINERS_REUSE_ENABLE/컨테이너 재사용 옵션을 임의로 켜거나 테스트의 DynamicPropertySource를 제거하지 않는다.
보고서: backend/build/reports/tests/test/index.html 및 integrationTest/index.html. 테스트가 실제로 끝나야 PASS로 기록한다. 다운로드/Java/Docker 오류는 차단으로 표시한다.

## 5. 기존 5차 프론트와 합쳐 확인

```powershell
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:unit
Push-Location .\frontend
npm.cmd run test:auth-nfc-ui
npm.cmd run test:participation-ui
npm.cmd run test:results-ui
npm.cmd run test:content-ui
npm.cmd run test:discovery-ui
npm.cmd run test:operations-ui
Pop-Location
```

기존 브라우저 검사들은 로컬 API fixture이며 실제 운영 권한이나 데이터 검증과 구별한다. 각각 실패하면 다음 검사로 넘어가기 전에 해당 보고서를 확인한다. 브라우저가 설치되지 않았다면 로컬에서 Playwright Chromium 설치가 필요할 수 있다.
실제 미션 수동 점검은 OPERATIONS.md 순서를 따른다. 기존 테스트 계정에서 공식 FAST TAP/최초 SYNC/최초 완료는 돌이킬 수 있는 단순 미리보기가 아니므로 자동 실행 도구에 넣지 않았다. 이를 실제 계정에서 실행하려면 별도로 승인된 테스트 시나리오가 필요하다.

## 복구

```powershell
Set-Location $repo
git apply -R --check $patch
if ($LASTEXITCODE -ne 0) { throw '추가 수정과 충돌. 강제 복구하지 마세요.' }
git apply -R $patch
```

UI 1~5차는 유지된다. 이 패치는 실제 DB를 변경하지 않으므로 DB 복구/초기화 명령도 없다. 생성 SQL·점검 JSON은 검토 후 개별 파일로 관리한다. commit/push/PR/merge/배포는 승인받지 않았으며 실행하지 않았다.
