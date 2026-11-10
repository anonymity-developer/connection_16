# 사후 운영 증분 — 적용 대기 / Windows PowerShell

오늘 사용자 PC 실행을 기다리지 않고 격리 Work에서 개발했다. 아래는 **추후 적용할 때 쓰는 안내**다. 현재 Windows 폴더에 적용돼 있다고 가정하지 않는다.

## 선행 순서

기존 UI v5 후속 → `final-over-v5-followup.patch` → `missions-over-final-integration.patch` → `awards-over-mission-final.patch` → `rehearsal-over-awards.patch` → 이번 `post-event-over-rehearsal.patch`.

운영 준비/옛 QA ZIP을 별도로 중복 적용하지 않는다. 앞 네 증분은 원래 ZIP의 가이드로 확인한다. 현재 증분은 그 뒤의 차이만 포함하며 인증/NFC/보상/마이그레이션/운영 일정 변경이 없다.

## 백업·충돌 검사·적용

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$bundle = 'C:\Users\amy\SSAFY_connection16\TAGME_post_event_86ad1dd'
$patch = Join-Path $bundle 'post-event-over-rehearsal.patch'
Set-Location $repo
git status --short
git rev-parse --short HEAD
# 기준 HEAD 86ad1dd, 선행 UI/기능 변경의 dirty는 정상
$backup = Join-Path (Split-Path $repo) ('tagme-before-post-event-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
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
if ($LASTEXITCODE -ne 0) { throw '선행 통합 상태와 다릅니다. 강제 적용하지 마세요.' }
git apply $patch
if ($LASTEXITCODE -ne 0) { throw '증분 적용 실패' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '공백 검사 실패' }
```

변경 목록의 파일만 백업하며 .env/비밀번호/토큰을 읽지 않는다. --reject/3way/reset/clean/수동 전체 덮어쓰기로 충돌을 무시하지 않는다. 충돌 시 적용을 멈추고 선행 순서·추가 편집 내용을 비교한다.

## 검증 — 추후 실행용

```powershell
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:unit
# Python3 표준 라이브러리 테스트. pandas 로딩 검사는 pandas가 있을 때만 실행
python scripts/post-event/test_activity_dataset.py -v
python scripts/post-event/activity_dataset.py --fixture-output docs/post-event/SYNTHETIC_activity.csv
# pandas 2 이상이 있는 개인 분석 환경에서만: 스키마/UTC 검사, 퍼널 계산 없음
python scripts/post-event/pandas_prepare.py docs/post-event/SYNTHETIC_activity.csv
# JDK21 + Docker Desktop의 독립 Testcontainers만 사용, 외부 DB override 이름 차단
.\scripts\rehearsal\Invoke-Rehearsal.ps1 -Backend -FullRegression
# 기존 브라우저 fixture + 새 행사 결과 fixture. 실제 사용자/운영 API 아님
.\scripts\rehearsal\Invoke-Rehearsal.ps1 -Browser
```

기존 runner의 핵심 Backend 대상에 PostEventIntegrationTest를 추가했다. FullRegression은 기존 7개까지 총10개 통합 클래스이며, 브라우저에는 check-post-event-ui.mjs가 추가됐다. 외부 MM mock, 독립 MySQL/Redis 컨테이너만 사용한다. 실제 Compose/DB/볼륨 초기화 없음. 환경 오류는 미검증이며 같은 설치를 반복하지 않는다. JDK17 순수 export smoke PASS는 Java21/Spring/API/DB PASS가 아니다.

개별 새 API 회귀만 추후 실행하려면 backend에서 `gradlew.bat --no-daemon integrationTest --tests com.tagme.integration.PostEventIntegrationTest --rerun-tasks`를 사용한다. 실행 전에 전용 테스트 shell·Docker·JDK21 및 외부 설정 없음이 확인돼야 한다. 직접 운영 DB/API 대상으로 실행하는 smoke tool은 제공하지 않는다.

## 화면 검수

권한 없는 계정은 행사 결과/내보내기/참가 기록 접근 거절. ADMIN은 `/admin?tab=results`의 0명/미설정·부분 참여·완성·대기 상태와 조회 시각/자료 지문 확인. 재조회 후 실제 수치 변화/동점/복수1위/숨김·철회 반영을 확인한다. CSV/JSON/MD에는 원문·닉네임·실명·MM ID가 없어야 한다. 개인별 활동 로그 다운로드 버튼은 없어야 한다. 360/375/390/412/430/1440px에서 표 내부 스크롤만 발생하고 전체 화면 가로 넘침 없이 버튼/포커스 확인. 기존 미션·수집판·시상 기능도 유지되는지 확인한다.

## 복구

추가 편집 전에는 이번 증분만 역적용한다.

```powershell
git apply -R --check $patch
if ($LASTEXITCODE -ne 0) { throw '후속 편집과 충돌합니다. 백업과 비교하고 강제 복구하지 마세요.' }
git apply -R $patch
if ($LASTEXITCODE -ne 0) { throw '증분 복구 실패' }
git status --short
```

선행 패치·실제 DB 데이터는 유지된다. 새 migration/수상 확정/로그 반출 활성화/자동 삭제 없음. Git commit/push/PR/merge/배포하지 않았다.
