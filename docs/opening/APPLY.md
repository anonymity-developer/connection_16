# 오픈 준비 점검 후속 — 적용 대기

오늘 로컬 명령 실행을 요구하지 않고 Work 격리 환경에서 개발·검증했다. 이 문서는 **나중에 적용할 때 쓰는 절차**다. 사용자 폴더에 패치가 적용돼 있다고 가정하지 않는다.

기존 UI v5 후속 상태 → final-over-v5-followup.patch → missions-over-final-integration.patch → awards-over-mission-final.patch → rehearsal-over-awards.patch → post-event-over-rehearsal.patch → 이번 **opening-over-post-event.patch**.

옛 운영 준비/QA ZIP을 따로 중복 적용하지 않는다. 새 서버 기능·UI·DDL·행사 날짜·동의문·문항은 추가하지 않는다. patch는 사후 운영 통합 대비 운영 도구/문서/회귀만 바꾼다.

## 추후 PowerShell 백업·검사·적용

```powershell
$repo = 'C:\Users\amy\SSAFY_connection16\connection_16-ui-review'
$bundle = 'C:\Users\amy\SSAFY_connection16\TAGME_opening_check_86ad1dd'
$patch = Join-Path $bundle 'opening-over-post-event.patch'
Set-Location $repo
git status --short
git rev-parse --short HEAD
# HEAD86ad1dd 및 선행 패치의 dirty 상태는 정상
$backup = Join-Path (Split-Path $repo) ('tagme-before-opening-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
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
```

실제 .env·자격증명·사용자 데이터 파일을 읽거나 백업하지 않는다. --reject/3way/reset/clean/전체 파일 덮어쓰기로 충돌을 무시하지 않는다.

## 읽기 전용 진단 — 추후 사용용

```powershell
# 입력/연결 없음: 실제 설정 미검증과 승인 목록을 JSON으로 출력
node scripts/readiness/opening-check.mjs
# 같은 명령의 PowerShell 래퍼
.\scripts\readiness\Invoke-OpeningCheck.ps1
# 이미 승인된 방식으로 취득한 비밀정보 없는 구성 메타데이터만 검사
.\scripts\readiness\Invoke-OpeningCheck.ps1 -Snapshot 'C:\private\opening-metadata.json'
# 선택: 고정된 세 공개 endpoint만 익명 GET. 127.0.0.1 HTTP origin만 허용
.\scripts\readiness\Invoke-OpeningCheck.ps1 -LocalOrigin 'http://127.0.0.1:8080'
```

새 SQL 설계 `scripts/readiness/opening-read-only.sql`은 구성 메타데이터만 같은 READ ONLY 트랜잭션에서 조회하고 ROLLBACK한다. **어떤 명령도 이 SQL에 자동 연결/실행하지 않는다.** 승인된 운영자 절차에서 나중에 사용할 SELECT 설계이며 Work 실행 미검증이다. MySQL 고객 도구의 인증정보·raw 출력·정책 본문·태그 비밀을 파일에 옮기지 말고 opening_snapshot JSON 하나만 사용한다. 파일은 운영 문항(비개인 정보)을 포함할 수 있으므로 비공개 보관한다. 기본 점검은 .env를 읽지 않으며 snapshot 인수는 .json만 허용한다.

자료의 capturedAt과 검사 observedAt, UTC 원본(마이크로초 유지)·KST 초 단위 표시를 대조한다. 과거 자료가 현재 환경을 입증하지 않으며 설정 변경 후에는 새 자료가 필요하다. 미래 capturedAt은 시계 확인이 필요하여 BLOCKED. KST 표시는 편의용이며 경계 판정은 원본 UTC 마이크로초를 사용한다.

- JSON 상태 READY는 입력 메타데이터/선택한 로컬 공개 응답 범위만 의미한다.
- 실제 MM 승인·수신·관리자·미션·실물 NFC는 NOT_VERIFIED, 운영 승인 항목은 NEEDS_APPROVAL로 남긴다. CLI 종료코드2는 예상된 NO_GO_PENDING_EVIDENCE, 1은 입력/실행 오류다. 전체 GO는 자동 발급하지 않는다.
- 선택 GET은 /api/v1/event, /api/v1/auth/config, /actuator/health만 사용한다. credentials omit/redirect error/cache no-store이며 로그인을 시도하거나 사용자 데이터/OTP/프로필/관리자 endpoint를 호출하지 않는다.
- 인증 available=false·health DOWN·미설정/종료 상태를 관찰하면 BLOCKED로 표시한다. healthy 공개 응답만으로 외부 MM/기기/전체 준비를 READY로 승격하지 않는다.
- 선택 운 점수 설정0개는 정상 선택 사항이다. 진단/보고서가 실제 키·계정·태그 URL/해시·정책 본문·문항 내용을 출력하지 않는다. raw 자격증명 JSON은 입력하지 않는다. 금지된 키를 포함한 자료는 거절한다.

## 추후 회귀

```powershell
npm.cmd --prefix frontend run lint
npm.cmd --prefix frontend run build
npm.cmd --prefix frontend run test:unit
python scripts/post-event/test_activity_dataset.py -v
node scripts/readiness/check-opening-contracts.mjs
# 기존 안전 리허설 재사용: JDK21/Docker 및 전용 테스트 shell이 있을 때만
.\scripts\rehearsal\Invoke-Rehearsal.ps1 -Backend -FullRegression
```

모든 새 Java/DB 회귀를 재작성하지 않았다. 기존 AuthPropertiesBindingTest/MattermostSenderSessionTest/OperationalReadinessTest와 Participation/Results/Content/Operations/EventRehearsal/PostEvent 통합 테스트를 재사용한다. 실제 Spring/SQL/권한/브라우저/PowerShell 결과는 이번 Work에서 미검증이다. 실행 환경 설치 반복·H2 대체·운영 DB smoke를 하지 않는다.

## 복구

```powershell
git apply -R --check $patch
if ($LASTEXITCODE -ne 0) { throw '추가 편집과 충돌합니다. 백업과 비교하세요.' }
git apply -R $patch
git status --short
```

이번 증분만 제거하며 선행 UI·미션·시상·리허설·사후 운영 변경을 보존한다. 데이터 정리/초기화/키 발급/수동 지급/commit/push/배포는 없다.
