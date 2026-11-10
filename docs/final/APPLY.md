# Windows PowerShell — 최종 통합 적용

> 후속 미션 기획 변경은 [최신 적용 가이드](../missions-final/APPLY.md)와 [구현 요약](../missions-final/SUMMARY.md)을 우선한다. 이 문서의 실행 결과는 당시 이력이다.

## 적용 기준 / 패치 선택

**기본 경로:** HEAD `86ad1dd` + UI 1~5차 + v5 후속 패치가 이미 미커밋 적용된 `connection_16-ui-review`. 운영 준비/QA는 아직 미적용.

- 이 상태에는 **final-over-v5-followup.patch 하나만** 적용한다. 운영 준비·QA·qa-over-readiness 정정·이번 추가 수정이 한 번씩 포함되어 있다.
- 구 ZIP의 operational-readiness.patch/qa-over-86ad1dd.patch/qa-over-readiness.patch를 이 패치 전후에 별도로 반복 적용하지 않는다. 기존 UI v5 ZIP도 다시 적용하지 않는다.
- **대안:** 이미 UI 후속 → 운영 준비 → 주 QA → qa-over-readiness까지 정확히 모두 적용했다면 polish-over-prepared-qa.patch만 사용한다. 이 두 최종 패치는 서로 대안이며 같은 폴더에 둘 다 적용하지 않는다.
- 원본 86ad1dd에 UI 후속조차 없다면 현재 패치를 바로 적용하지 않는다. 먼저 기존 UI 전체/후속으로 기준 상태를 재현하거나 별도 검토 worktree를 준비해야 한다. 사용자 현재 폴더는 이 단계가 완료된 상태다.
- 시상 ZIP은 미통합이다. f77aba1을 checkout/merge하거나 현재 UI를 그 버전으로 내리지 않는다.

구 산출물의 선행 관계(수동 분할 경로를 선택할 때):

| 순서 | 산출물 | 선행 조건 |
| --- | --- | --- |
| 1 | v5 후속 UI | UI 1~5차 적용 완료 |
| 2 | 운영 준비 | 기존 UI와 독립된 도구/테스트 추가 |
| 3 | 주 QA | 86ad1dd 런타임/테스트와 호환, 먼저 --check |
| 4 | qa-over-readiness | 운영 준비 도구가 실제 적용된 뒤만 |
| 5 | polish-over-prepared-qa | 위 1~4가 모두 완료된 상태만 |

현재 사용자에게 권장하는 경로는 이 수동 2~5 단계를 **final-over-v5-followup.patch 하나로 대체**하는 것이다.

## 1. 백업 → 충돌 검사 → 적용

ZIP을 Downloads에 압축 해제한다. 다른 위치라면 `$bundle`만 바꾼다.

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$bundle = Join-Path $HOME 'Downloads\TAGME_final_integration_86ad1dd'
$patch = Join-Path $bundle 'final-over-v5-followup.patch'
Set-Location $repo
git status --short
git rev-parse --short HEAD
# HEAD는 86ad1dd. UI 변경 때문에 dirty인 것은 정상.
$backup = Join-Path (Split-Path $repo) ('tagme-before-final-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
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
# 실제 .env/비밀번호/토큰은 대상에 없고 백업하지 않음.
git apply --check $patch
if ($LASTEXITCODE -ne 0) { throw '기준 상태와 차이가 있습니다. 덮어쓰지 말고 적용 이력을 확인하세요.' }
git apply $patch
if ($LASTEXITCODE -ne 0) { throw '적용 실패. 추가 조작 전에 상태 확인 필요.' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '공백/패치 검사 실패' }
```

이미 일부 운영/QA를 적용해 주 패치가 충돌한다면 --reject/3way/강제 복사를 하지 않는다. 적용 이력을 확인하고 정확한 준비 상태일 때만 위의 **대안 패치**를 --check한다. 두 최종 패치의 적용 대상을 섞지 않는다.

## 2. 테스트

```powershell
npm.cmd --prefix frontend run lint
if ($LASTEXITCODE -ne 0) { throw 'lint 실패' }
npm.cmd --prefix frontend run build
if ($LASTEXITCODE -ne 0) { throw 'build 실패' }
npm.cmd --prefix frontend run test:unit
if ($LASTEXITCODE -ne 0) { throw '단위 실패' }
# 현재 최종 통합 기대 결과: 42 PASS (모두 fixture/단위, 실제 계정 요청 아님)
```

node_modules가 없다면 기존 lockfile의 `npm.cmd --prefix frontend ci`. 새 패키지는 없다.

```powershell
# 실제 Java/DB 회귀: JDK21, Docker Desktop 준비 후 별도 임시 Testcontainers 사용
.\scripts\qa\Run-PrelaunchQA.ps1 -Backend
# 브라우저 fixture 계약 + 360/375/390/412/430/1440px UI 검사
.\scripts\qa\Run-PrelaunchQA.ps1 -Browser
```

PowerShell 실행 정책으로 스크립트가 차단되면 전역 정책을 바꾸지 말고 docs/qa/POWERSHELL.md의 명령을 직접 실행한다. 프론트 검사만 통과했다고 Java/DB/실물 NFC를 PASS 처리하지 않는다. 통합 테스트의 datasource와 MM/provider mock을 실제 운영 DB/계정으로 바꾸지 않는다.

## 3. 화면 확인

```powershell
npm.cmd --prefix frontend run dev -- --host 127.0.0.1
# 또는 build한 production 화면 확인
npm.cmd --prefix frontend run preview -- --host 127.0.0.1
```

홈/카드/상세/모바일/운영자 확인은 docs/final/REVIEW.md를 따른다. 인증 비활성·정책/일정 미설정이라는 현재 로컬 상태를 화면 검토 목적으로 우회하지 않는다. 필요한 상세 폼은 기존 로컬 fixture 계약 테스트를 이용한다. 실제 개인 MM 로그인·DM·공식 게임 기회 소모를 자동 실행하지 않는다.

## 4. 읽기 전용 설정 확인 (선택)

```powershell
node scripts/readiness/audit.mjs --probe http://127.0.0.1:8080
```

실제 API 포트가 다르면 로컬 포트만 바꾼다. 127.0.0.1 GET만 허용하며 설정 상태만 출력한다. DB 조회가 필요하면 docs/readiness/POWERSHELL.md의 READ ONLY 절차를 사용한다. dev SQL 생성기는 기본 ROLLBACK이며, 실제 정책/일정을 설정하는 명령이 아니다. 실제 MM 자격증명 제거/인증 비활성화는 사용자 보고이며 이번 패치가 환경변수를 바꾸지 않는다.

## 5. 이번 통합만 복구

추가 편집 전이라면 **실제로 적용한 최종 패치 하나**를 역적용한다.

```powershell
Set-Location $repo
git apply -R --check $patch
if ($LASTEXITCODE -ne 0) { throw '추가 편집과 충돌합니다. 강제 복구하지 마세요.' }
git apply -R $patch
if ($LASTEXITCODE -ne 0) { throw '복구 실패. 백업과 비교하세요.' }
git status --short
```

기본 패치 역적용은 기존 UI 1~5차+후속 상태로 돌아간다. 대안 패치를 적용했다면 `$patch`는 그 대안 파일이어야 하며, 역적용 후 운영 준비·QA가 적용된 상태를 유지한다. 충돌 시 백업 대상 파일과 비교해 필요한 부분만 복구하고 기존 UI를 일괄 덮어쓰지 않는다.

DB를 변경하지 않았으므로 DB 복구/초기화 SQL이 없다. down -v/FLUSH/DELETE/reset --hard/clean, commit/push/PR/merge/배포는 하지 않는다.
