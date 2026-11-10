# Windows PowerShell — 리허설 후속 증분

## 적용 기준

현재 Windows 폴더에 선행 패치가 모두 적용됐다고 가정하지 않는다. 순서는 아래와 같다.

1. 기존 UI v5 후속 상태
2. final-over-v5-followup.patch
3. missions-over-final-integration.patch
4. awards-over-mission-final.patch
5. 이번 **rehearsal-over-awards.patch**

각 선행 ZIP의 APPLY.md로 백업/--check를 먼저 수행한다. 운영 준비·주 QA·qa-over-readiness를 따로 중복 적용하지 않는다. 기존 미커밋 UI에 강제 복사/--reject/3way/reset/clean하지 않는다.

## 백업 → 검사 → 적용

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$bundle = 'C:\Users\amy\SSAFY_connection16\TAGME_event_rehearsal_86ad1dd'
$patch = Join-Path $bundle 'rehearsal-over-awards.patch'
Set-Location $repo
git status --short
git rev-parse --short HEAD
# HEAD=86ad1dd, 기존 UI/기능 변경 때문에 dirty는 정상
$backup = Join-Path (Split-Path $repo) ('tagme-before-rehearsal-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
Get-Content (Join-Path $bundle 'CHANGED_FILES.txt') | ForEach-Object {
  $source = Join-Path $repo $_
  if (Test-Path -LiteralPath $source -PathType Leaf) {
    $target = Join-Path $backup $_
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -ErrorAction Stop
  }
}
Copy-Item (Join-Path $bundle 'CHANGED_FILES.txt') $backup
git apply --check $patch
if ($LASTEXITCODE -ne 0) { throw '최신 시상 통합 기준과 다릅니다. 적용 이력을 먼저 확인하세요.' }
git apply $patch
if ($LASTEXITCODE -ne 0) { throw '증분 적용 실패' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '공백 검사 실패' }
```

.env/비밀번호/토큰은 대상 목록에 없으며 읽거나 백업하지 않는다. 새 DB migration/운영 설정/패키지 설치는 없다.

## 로컬 리허설 — 기본 → API/DB → 브라우저 → 선택 장애

프로젝트 lockfile의 Node24, JDK21, Docker Desktop을 사용한다. 테스트는 Compose에 붙지 않고 임시 Testcontainers MySQL/Redis에만 연결한다. 실제 API URL/DB 주소/사용자 계정 입력 옵션은 제공하지 않는다.

```powershell
Set-Location $repo
# 1. 기본: diff / frontend lint / build / 단위 (기대 60 PASS)
.\scripts\rehearsal\Invoke-Rehearsal.ps1
# 2. Spring test/bootJar + 새 HTTP·DB 여정/Infrastructure health
.\scripts\rehearsal\Invoke-Rehearsal.ps1 -Backend
# 3. 기존 production fixture 브라우저 계약 (실제 계정/실서버 요청 없음)
.\scripts\rehearsal\Invoke-Rehearsal.ps1 -Browser
# 4. 전체 기존 Java 회귀 7개 클래스까지 포함하려면
.\scripts\rehearsal\Invoke-Rehearsal.ps1 -Backend -FullRegression
# 5. 기본 API/DB 리허설이 통과한 뒤, 임시 컨테이너 pause/unpause만 선택 실행
.\scripts\rehearsal\Invoke-Rehearsal.ps1 -Backend -Faults
```

- -Faults/-FullRegression은 -Backend와 함께만 허용한다. 장애 검사는 테스트가 직접 만든 컨테이너 ID만 사용하고 테스트 안에서 finally로 unpause한다. 실제 서비스/Compose/기존 볼륨을 stop/remove/reset하지 않는다.
- 실제 5초 준비/10초 경과를 기다리는 HTTP 여정이 하나 있어 API 테스트는 최소 15초 이상 걸린다. 브라우저 터치/실제 NFC를 실행했다는 의미는 아니다.
- 정상 리허설 뒤 -Faults를 호출해도 Gradle의 과거 skip 결과가 재사용되지 않도록 플래그와 실행 ID를 integrationTest input으로 등록했다. Faults는 JUnit XML에서 해당 테스트가 실제 실행·성공했을 때만 PASS 처리한다.
- 외부 datasource/Flyway/Redis/config/auth 환경변수 이름이 있으면 backend 실행을 거절한다. 값은 읽거나 출력하지 않는다. 운영 shell 값을 옮기지 말고 별도 테스트 shell에서 실행한다. 이 도구가 실제 .env를 읽어 제거하거나 운영 환경변수를 바꾸지 않는다.
- 테스트용 fault/run-id 플래그만 프로세스 안에서 잠시 설정하고 끝나면 이전 값으로 복원한다.
- PowerShell 실행 정책이 막으면 전역 정책을 완화하지 말고 아래 개별 명령을 사용한다.

각 실행의 상태 JSON은 `output/rehearsal/rehearsal-*.json`에 남는다. 생략한 Backend/Browser/Faults/실기기/앱 재시작은 UNVERIFIED다. 실제 테스트 세부 결과는 `backend/build/test-results/integrationTest`, `backend/build/reports/tests/integrationTest`와 기존 `output/playwright` 결과를 함께 본다. 환경/컴파일 오류와 제품 회귀 실패를 구분한다.

개별 실행:

```powershell
npm.cmd --prefix frontend run lint
if ($LASTEXITCODE -ne 0) { throw 'lint 실패' }
npm.cmd --prefix frontend run build
if ($LASTEXITCODE -ne 0) { throw 'build 실패' }
npm.cmd --prefix frontend run test:unit
if ($LASTEXITCODE -ne 0) { throw '단위 실패' }
Push-Location backend
.\gradlew.bat --no-daemon test bootJar
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Java 빌드 실패' }
.\gradlew.bat --no-daemon integrationTest --tests 'com.tagme.integration.EventRehearsalIntegrationTest' --tests 'com.tagme.integration.InfrastructureIntegrationTest'
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'HTTP/DB 리허설 실패' }
Pop-Location
```

개별 Gradle 명령은 기존 결과가 up-to-date일 수 있으므로 새 실제 실행 확인이 필요하면 위 runner를 이용한다. 직접 실행의 opt-in 장애 환경변수는 임의 설정하지 말고 runner의 -Faults를 사용한다. JDK21/Docker가 없으면 설치를 반복하거나 H2로 대체하지 않는다. 이 경우 [RESULTS.md](RESULTS.md)의 미검증 항목을 유지한다.

Playwright 브라우저가 로컬에 없다는 오류가 나면 기존 Playwright package의 브라우저 설치를 한 번 준비하거나 실제 브라우저 검수를 미실행으로 남긴다. 새 테스트 라이브러리는 필요하지 않다. 실화면은 [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md), 행사 대응은 [RUNBOOK.md](RUNBOOK.md)를 따른다.

## 복구

추가 편집 전이라면 이번 증분만 역적용해 시상 통합 상태로 돌아간다.

```powershell
git apply -R --check $patch
if ($LASTEXITCODE -ne 0) { throw '추가 변경과 충돌합니다. 백업과 비교하고 강제 복구하지 마세요.' }
git apply -R $patch
if ($LASTEXITCODE -ne 0) { throw '리허설 증분 복구 실패' }
git status --short
```

선행 패치까지 되돌릴 때는 이번 증분 → 시상 → 미션 → final 통합의 역순으로 각 가이드를 따른다. DB 초기화/볼륨 삭제/수동 재지급/실제 기록 복구 SQL은 없다. 코드 역적용으로 이미 운영에서 발생한 정상 데이터가 삭제되는 것도 아니다. commit/push/PR/merge/배포는 하지 않는다.
