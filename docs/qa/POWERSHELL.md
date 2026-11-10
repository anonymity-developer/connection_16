# PowerShell: QA 패치 적용·검증·복구

기준 HEAD 86ad1dd, UI 1~5차 미커밋 상태를 보존한다. 현재 미적용인 UI 후속/운영 준비 ZIP을 임의 적용하지 않는다. 이 패치에는 기존 두 ZIP 전체를 재포장하지 않았다.

## 1. 주 패치 적용

ZIP을 Downloads에 압축 해제하고 `$bundle` 경로를 확인한다.

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$bundle = Join-Path $HOME 'Downloads\TAGME_prelaunch_QA_86ad1dd'
$patch = Join-Path $bundle 'qa-over-86ad1dd.patch'
Set-Location $repo
git status --short
git apply --check $patch
if ($LASTEXITCODE -ne 0) { throw '추가 변경과 충돌합니다. 덮어쓰지 마세요.' }
$backup = Join-Path (Split-Path $repo) ('tagme-before-qa-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
Get-Content (Join-Path $bundle 'CHANGED_FILES.txt') | ForEach-Object {
  $source = Join-Path $repo $_
  if (Test-Path -LiteralPath $source -PathType Leaf) {
    $target = Join-Path $backup $_
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -ErrorAction Stop
  }
}
git apply $patch
if ($LASTEXITCODE -ne 0) { throw '적용 실패. 먼저 상태를 확인하세요.' }
```

qa-over-86ad1dd.patch는 원본 기준으로 생성했지만 기존 5차/후속/운영 패치와 다른 영역만 수정한다. 실제 적용 순서는 반드시 --check로 확인한다. 승인되지 않은 시상 코드 등 다른 브랜치가 있으면 강제 적용하지 않는다.

## 2. 실행 가능한 프론트/순수 상태 검사

```powershell
node --version # 기존 프로젝트 요구 Node 24
.\scripts\qa\Run-PrelaunchQA.ps1
# 스크립트 실행 정책으로 차단되면 전역 정책을 바꾸지 말고 아래 명령 직접 실행:
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:unit
```

node_modules가 없으면 기존 lockfile로 npm.cmd --prefix frontend ci. 신규 패키지는 없다. 원본+QA 단위 21개, 두 기존 ZIP+QA+선택 도구 정정 조합은 TEST_RESULTS.md에 기록된 수를 확인한다. 구성별로 기존 UI 테스트 수가 다르므로 단순 숫자 차이를 실패로 판단하지 않는다.

## 3. JDK21·Docker 준비 후 격리 서버 검사

```powershell
javac -version # 21
# 기존 Docker 서비스가 Healthy여도 이 테스트는 별도 임시 컨테이너를 사용
.\scripts\qa\Run-PrelaunchQA.ps1 -Backend
```

Backend 플래그는 JDK21/daemon 확인 후 test bootJar, 고정 7개 integrationTest 클래스를 실행한다. 실제 MM/검수 제공자는 mock이며 각 테스트가 fresh MySQL/Redis 주소를 DynamicPropertySource로 바인딩한다. .env를 직접 읽거나 운영 Spring 앱을 띄우지 않는다. 현재 DB 연결을 테스트로 넘기지 않는다.

기존 테스트 내부 DELETE/FLUSH를 직접 Compose DB에서 실행하지 않는다. 재사용 컨테이너/테스트 datasource override를 추가하지 않는다. 성공 전에는 backend/build/reports/tests의 결과를 PASS로 적지 않는다. Work에서는 JDK17만 있고 Docker가 없어 Java 컴파일/실행을 검증하지 못했다.

## 4. 브라우저 회귀 및 실기기

```powershell
# build 이후 Playwright 실행 환경에서, 운영 서버 요청 없는 기존 fixture 계약 검사
.\scripts\qa\Run-PrelaunchQA.ps1 -Browser
# 새 FAST TAP 회귀만 따로:
npm.cmd --prefix frontend run test:results-ui
```

Playwright 브라우저가 없다면 로컬에서 설치/준비가 필요하다. 이 스크립트는 기존 계약 테스트의 360/390/430px·PC fixture 검사를 재사용한다. 실제 Safari·Android·NFC·키보드는 대체하지 않는다. raw 개인정보 화면 캡처를 외부 전달하지 않는다.

## 5. 선택: 이전 운영 점검 도구 정정

`TAGME_operational_readiness_86ad1dd.zip`의 패치를 **나중에 실제로 적용한 경우에만** 아래 overlay를 적용한다. 아직 운영 패치가 없다면 이 단계는 건너뛴다. 주 QA 패치와는 독립적이다.

```powershell
$correction = Join-Path $bundle 'qa-over-readiness.patch'
git apply --check $correction
if ($LASTEXITCODE -ne 0) { throw '운영 점검 패치 적용 여부/버전을 확인하세요.' }
git apply $correction
if ($LASTEXITCODE -ne 0) { throw '도구 정정 적용 실패' }
node --test scripts/readiness.test.mjs
```

정정은 운 점수 선택 설정 0건을 정상 허용하는 것으로, 실제 DB·운 점수·시상 규칙을 바꾸지 않는다. 기존 OPERATIONS.md의 운 설정 ‘필수’ 표현은 이번 QA_REPORT.md 설명으로 정정한다. 후속 패치 파일을 반복 적용하지 않는다.

## 6. QA만 복구

추가 편집 전에 선택 overlay를 적용했다면 먼저 해당 overlay만 역적용한 뒤 주 QA를 역적용한다.

```powershell
# overlay 적용했을 때만:
# git apply -R --check $correction
# 검사 통과 후 git apply -R $correction

git apply -R --check $patch
if ($LASTEXITCODE -ne 0) { throw '추가 수정과 충돌합니다. 강제 복구하지 마세요.' }
git apply -R $patch
```

기존 UI/운영 변경은 유지된다. reset --hard / clean / down -v / DB 초기화 / 토큰 출력은 하지 않는다. 실제 DB를 변경하지 않았으므로 DB 복구 SQL도 제공하지 않는다. commit/push/PR/merge/배포 없음.
