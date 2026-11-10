# Windows PowerShell 적용 — 미션 기획 확정 후속

## 기준과 순서

사용자 현재 폴더: HEAD 86ad1dd + UI 1~5차/v5 후속. **이전 final 통합은 아직 미적용**.

1. 기존 TAGME_final_integration_86ad1dd.zip의 `final-over-v5-followup.patch`를 먼저 적용한다.
2. 이번 ZIP의 `missions-over-final-integration.patch`만 추가한다.
3. 프론트·서버·브라우저 검사 후 실제 적용 여부를 판단한다.

구 운영 준비·주 QA·qa-over-readiness는 이전 final 통합에 이미 포함되어 있다. 별도로 다시 적용하지 않는다. 이번 ZIP은 새 증분이며 기존 final 통합을 대체하지 않는다. 86ad1dd 원본이나 v5-only 상태에 새 증분을 직접 적용하지 않는다.

## 백업 → 검사 → 적용

압축 해제 경로가 다르면 $previous/$bundle만 변경한다. 현재 폴더에 추가 편집이 있으면 --check가 실패할 수 있으며 강제 복사/--reject/3way/reset을 하지 않는다.

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$previous = 'C:\Users\amy\SSAFY_connection16\TAGME_final_integration_86ad1dd'
$bundle = 'C:\Users\amy\SSAFY_connection16\TAGME_mission_plan_final_86ad1dd'
$oldPatch = Join-Path $previous 'final-over-v5-followup.patch'
$newPatch = Join-Path $bundle 'missions-over-final-integration.patch'
Set-Location $repo
git status --short
git rev-parse --short HEAD
# HEAD=86ad1dd, 기존 UI 변경 때문에 dirty는 정상
$backup = Join-Path (Split-Path $repo) ('tagme-before-mission-plan-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
$targets = @((Get-Content (Join-Path $previous 'CHANGED_FILES.txt')) + (Get-Content (Join-Path $bundle 'CHANGED_FILES.txt'))) | Sort-Object -Unique
$targets | Set-Content (Join-Path $backup 'TARGETS.txt') -Encoding UTF8
foreach ($relative in $targets) {
  $source = Join-Path $repo $relative
  if (Test-Path -LiteralPath $source -PathType Leaf) {
    $target = Join-Path $backup $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -ErrorAction Stop
  }
}
# .env/개인정보/자격증명은 대상 목록에 없으며 읽거나 백업하지 않음
# 먼저 이전 통합이 아직 미적용인 현재 상태에서:
git apply --check $oldPatch
if ($LASTEXITCODE -ne 0) { throw '이전 통합 기준과 다릅니다. 적용 이력부터 확인하세요.' }
git apply $oldPatch
if ($LASTEXITCODE -ne 0) { throw '이전 통합 적용 실패' }
# 이어서 이번 증분:
git apply --check $newPatch
if ($LASTEXITCODE -ne 0) { throw '후속 증분 기준과 다릅니다. 아래 복구 절차를 확인하세요.' }
git apply $newPatch
if ($LASTEXITCODE -ne 0) { throw '후속 적용 실패' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '공백 검사 실패' }
```

이미 이전 final 통합을 적용했다면 $oldPatch 단계만 건너뛰고 새 패치를 --check한다. 이번 패치까지 이미 적용했다면 재적용하지 않는다. 바이너리 에셋은 기존 final 기준을 유지하며 변경된 이미지/외부 패키지는 없다.

## 검증

```powershell
npm.cmd --prefix frontend run lint
if ($LASTEXITCODE -ne 0) { throw 'lint 실패' }
npm.cmd --prefix frontend run build
if ($LASTEXITCODE -ne 0) { throw 'build 실패' }
npm.cmd --prefix frontend run test:unit
if ($LASTEXITCODE -ne 0) { throw '단위 실패' }
# 기대: 50 PASS
# JDK21 / Docker Desktop 준비 후: Testcontainers 전용 DB만 사용
.\scripts\qa\Run-PrelaunchQA.ps1 -Backend
# 로컬 production fixture 화면 검사 (실제 사용자 요청/DM 아님)
.\scripts\qa\Run-PrelaunchQA.ps1 -Browser
```

backend 명령은 test/bootJar와 기존 7개 통합 클래스를 포함한다. Java mock 단위만 먼저 실행하려면:

```powershell
Push-Location backend
.\gradlew.bat --no-daemon test --tests 'com.tagme.participation.ShowMeAutomaticCompletionTest' --tests 'com.tagme.participation.ShowMeCompletionRuleTest' --tests 'com.tagme.participation.OperationalReadinessTest'
if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'SHOW ME 단위 실패' }
Pop-Location
```

node_modules가 없다면 기존 lockfile로 `npm.cmd --prefix frontend ci`. PowerShell 정책으로 차단되면 전역 정책을 바꾸지 말고 docs/qa/POWERSHELL.md의 명령을 직접 실행한다. 실제 API/프론트는 함께 검토한다. 프론트만 새 버전이고 API가 이전이면 새 재확인 POST가 지원되지 않아 기록 조회 오류가 날 수 있다.

화면 검수는 REVIEW.md를 따른다. 일정/동의·MM 인증 비활성 상태를 우회하지 않는다. 실제 운영 DB나 인증정보를 Testcontainers 테스트에 연결하지 않는다. 새 /missions/show-me/reconcile은 로그인과 CSRF가 필수이고 GET이나 공용 우회 URL로 바꾸지 않는다.

## 복구

추가 편집이 없다면 이번 증분부터 역순으로 제거한다.

```powershell
git apply -R --check $newPatch
if ($LASTEXITCODE -ne 0) { throw '추가 편집과 충돌합니다. 백업과 비교하고 강제 복구하지 마세요.' }
git apply -R $newPatch
if ($LASTEXITCODE -ne 0) { throw '후속 복구 실패' }
# 위 명령까지는 이전 final 통합 상태로 복귀.
# 이전 final도 이번에 적용했으며 함께 되돌리려는 경우에만:
git apply -R --check $oldPatch
if ($LASTEXITCODE -ne 0) { throw '이전 통합 역적용 충돌' }
git apply -R $oldPatch
if ($LASTEXITCODE -ne 0) { throw '이전 통합 복구 실패' }
git status --short
```

여기까지 역적용하면 사용자 최초 UI v5 후속 상태로 돌아간다. 충돌 시 개별 백업 파일과 diff를 비교한다. git reset --hard/clean, DB 초기화/볼륨 삭제/수동 보상 INSERT는 하지 않는다. 패치 적용·역적용 자체는 DB를 쓰지 않는다. 단, 나중에 실제 실행으로 생성된 정상 S 지급 기록은 코드 역적용으로 삭제되지 않는다. 정책을 되돌릴 경우 기록을 지우지 말고 별도 검토한다.
