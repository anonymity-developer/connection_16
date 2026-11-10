# Windows PowerShell — 시상 후보 증분 적용

## 기준

적용 순서는 **UI v5 후속 → final-over-v5-followup.patch → missions-over-final-integration.patch → awards-over-mission-final.patch**다. 이미 적용한 선행 패치를 반복 적용하지 않는다. 이번 ZIP은 시상 후속 증분만 포함한다. 운영 준비·구 QA·qa-over-readiness는 기존 final 통합에 포함되어 있으므로 따로 적용하지 않는다.

선행 ZIP의 APPLY.md로 각각 백업/--check/적용을 마친 뒤 이번 단계를 실행한다. 적용 여부가 불명확하거나 일부 파일만 수동 반영했다면 --reject/3way/강제 복사하지 않는다. 사용자 기존 미커밋 UI를 reset/clean하거나 원본 브랜치로 덮어쓰지 않는다.

## 백업 → 충돌 검사 → 적용

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$bundle = 'C:\Users\amy\SSAFY_connection16\TAGME_awards_final_86ad1dd'
$patch = Join-Path $bundle 'awards-over-mission-final.patch'
Set-Location $repo
git status --short
git rev-parse --short HEAD
# HEAD=86ad1dd, 기존 변경이 dirty인 것은 정상
$backup = Join-Path (Split-Path $repo) ('tagme-before-awards-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
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
if ($LASTEXITCODE -ne 0) { throw '최신 미션 통합 기준과 다릅니다. 적용 이력부터 확인하세요.' }
git apply $patch
if ($LASTEXITCODE -ne 0) { throw '적용 실패. 추가 조작 전 상태를 확인하세요.' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '패치 공백 검사 실패' }
```

.env/비밀번호/토큰은 변경 파일 목록에 없으며 읽거나 백업하지 않는다. DB 마이그레이션/SQL 적용 단계가 없다. 서버와 프론트를 같은 검토 상태로 실행해야 한다. 프론트만 새 버전이면 시상 API가 없는 이전 API에서 조회 오류가 발생할 수 있다.

## 테스트

```powershell
npm.cmd --prefix frontend run lint
if ($LASTEXITCODE -ne 0) { throw 'lint 실패' }
npm.cmd --prefix frontend run build
if ($LASTEXITCODE -ne 0) { throw 'build 실패' }
npm.cmd --prefix frontend run test:unit
if ($LASTEXITCODE -ne 0) { throw '단위 실패' }
# 기대: 54 PASS
# JDK17+ compiler 또는 JDK21의 순수 계산 확인 (DB/Spring 검증 아님):
.\scripts\awards\Run-CalculatorCheck.ps1
# JDK21 + Docker Desktop: Spring 컴파일/bootJar + 기존 격리 MySQL/Redis 통합 7개 클래스
.\scripts\qa\Run-PrelaunchQA.ps1 -Backend
# 실서버 요청 없이 production fixture 브라우저 검사 (기존 검사 + 새 시상 검사)
.\scripts\qa\Run-PrelaunchQA.ps1 -Browser
```

시상 관련 서버 검사만 먼저 실행하려면:

```powershell
Push-Location backend
.\gradlew.bat --no-daemon test --tests 'com.tagme.operations.AwardCalculatorTest'
if ($LASTEXITCODE -ne 0) { Pop-Location; throw '계산 단위 실패' }
.\gradlew.bat --no-daemon integrationTest --tests 'com.tagme.integration.OperationsIntegrationTest' --tests 'com.tagme.integration.ParticipationIntegrationTest'
if ($LASTEXITCODE -ne 0) { Pop-Location; throw '권한/시상/SHOW ME 통합 실패' }
Pop-Location
```

테스트는 해당 클래스의 @Container MySQL/Redis와 MM/provider mock을 사용한다. datasource를 운영 DB/Compose 실사용 DB로 바꾸지 않는다. 실제 계정·소원·하트·게임을 삭제/재생성하는 테스트 명령이 아니다. 실행 정책이 막으면 전역 정책을 완화하지 말고 문서의 개별 명령을 직접 실행한다.

브라우저는 `npm.cmd --prefix frontend run build` 후 frontend에서 `node scripts/check-awards-ui.mjs`로 시상 fixture만 실행할 수도 있다. 실제 브라우저 검증 성공은 이번 Work에서 확인하지 않았다. 로컬 화면은 /admin?tab=awards에서 관리자 권한으로 확인하되 실제 계정/DB 접근은 사용자가 승인한 환경에서만 수행한다. 정책/일정·관리자 권한을 화면 검토를 위해 우회하지 않는다.

## 되돌리기

추가 편집 전이라면 이번 증분만 역적용해 최신 미션 통합 상태로 돌아간다.

```powershell
git apply -R --check $patch
if ($LASTEXITCODE -ne 0) { throw '추가 변경과 충돌합니다. 백업과 비교하고 강제 복구하지 마세요.' }
git apply -R $patch
if ($LASTEXITCODE -ne 0) { throw '복구 실패' }
git status --short
```

충돌 시 백업과 개별 diff를 비교한다. 선행 패치를 함께 되돌리려면 이번 증분을 먼저 제거하고 미션 패치 → final 통합 패치의 기존 가이드를 역순으로 따른다. 실제 시상/DB 변경을 하지 않으므로 rollback SQL/DB 초기화는 없다. commit/push/PR/merge/배포 명령은 포함하지 않았다.
