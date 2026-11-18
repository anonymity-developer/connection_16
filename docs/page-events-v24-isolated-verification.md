# P0/P1 V24 격리 통합 검증 — 2026-11-18

이 문서는 일회용 DB와 합성 브라우저 API의 기술 검증이다. 실제 유지 DB에서의 재번호 안전성이나 PR 재번호·Push·병합·배포 승인을 의미하지 않는다.

## 후보와 보존 범위

- 브랜치: `review/page-events-v24-isolated-20261118`
- worktree: `.codex-log/integration-pages-v24` (원본 저장소 기준)
- 기반: 최신 확인 main `f244e3c` + #37 격리 후보 `e3839b0`
- 기존 P0 `8cdebc6` → 후보 `1b55d32`, P1 `c8451b5` → 후보 `d505142`
- 원본 #37 `1f565dc`, #39 `ecd75ee`, P0/P1 브랜치와 기존 격리 worktree는 유지한다. #39 코드는 포함하지 않는다.
- 작성자/커미터: `eda-21`. 후보의 추가 검증 커밋은 이 문서를 포함하는 브랜치 HEAD에서 확인한다.

기존 [퍼널 V23 검증](pr37-v23-isolated-verification.md)과 원본 P0/P1의 퍼널 V21→페이지 V23 결과는 그대로 보존한다. 이번 실행과 합산하거나 동일한 경로의 결과로 표시하지 않는다.

## SQL 순서와 내용

| 후보 버전 | 파일 | 내용 변경 |
| --- | --- | --- |
| V1~V20 | main 기존 SQL 및 repeatable SQL | 없음 |
| V21 | `V21__tap_recovery_evidence.sql` | main 원본 유지 |
| V22 | `V22__system_tap_recovery.sql` | main 원본 유지 |
| V23 | `V23__add_mission_funnel_events.sql` | #37의 V21 파일명만 변경한 기존 격리 후보 |
| V24 | `V24__add_page_event_analytics.sql` | P0의 V23 파일명만 변경 |

원본 SQL과 후보 SQL은 바이트 단위로 동일하다. 퍼널 SHA-256은 `6d13ac55946ae04e6d4554907cc8414763dbd209644c42726de5a5110da2d27e`, 페이지 SHA-256은 `867bc38550a3dee326f7c3be0037f2f870d8cb77811cc9d0b01ad46e342f5274`다. 페이지 SQL의 기존 주석 `V21`은 원본 퍼널 번호를 가리키며 후보에서는 V23이다. 주석도 바꾸지 않았다. V1~V24 번호 중복과 CREATE TABLE 이름 중복이 없고, main의 모든 migration 내용은 동일하다. 실제 인덱스/외래키 적용은 아래 MySQL 검사로 확인한다.

V24는 페이지 테이블 2개와 퍼널의 nullable 세션 digest 열만 추가한다. 과거 행의 digest는 NULL이고 소급 로그를 생성하지 않는다. 앱 롤백 시에도 테이블·열·Flyway 이력/checksum을 삭제·수정하지 않는다. 원본 퍼널 writer는 새 열을 쓰지 않고 계속 삽입할 수 있으나, 새 앱 배포 전 후보 SQL 적용과 실제 PR 통합 순서를 별도로 승인해야 한다.

## 충돌 해결

P0의 `backend/build.gradle`은 main의 UTC 검사 설정과 분석 observer의 기존 테스트 기본 비활성 설정을 함께 유지했다. 데이터 모델/ERD/진행 문서는 main TAP 복구와 퍼널·페이지 후보를 구분한다. P1의 관리자 셸은 main TAP 지원 화면과 기존 SYNC 퍼널을 유지하며 페이지 분석 라우트와 안정적인 `onDenied` callback을 추가했다. `test:admin-ui`의 TAP 지원 검사도 유지했다. `ParticipationRepository`의 main 인정 시각 기반 완료 조회는 유지하고, P0의 공통 커밋 이후 알림만 자동 통합됐다. 제품 UI 문구·스타일·기능을 새로 만들지 않았다.

## 검사 결과

핵심 실행은 UTC/Java 21 + 일회용 MySQL 8.4/Redis 7.4에서 `test bootJar integrationTest --tests ...`로 실행했다. JUnit XML 기준 단위 103개와 통합 45개(분석 15, 결과/퍼널 23, 기존 퍼널 마이그레이션 4, 새 V24 마이그레이션 3) 모두 통과, 실패/오류/건너뜀 0개이고 bootJar도 성공했다. `.codex-log/pages-v24-targeted-backend.log`, `pages-v24-targeted-summary.json`, `pages-v24-static-audit.json`에 근거를 보존한다.

프론트 lint·단위 123개·운영 빌드가 통과했다. `test:page-events-ui`, `test:admin-ui`(TAP 지원 포함), `test:results-ui`, `check-mission-flow-ui.mjs`도 통과했다. `/admin/events` 네 탭의 360/390/430/768/1440px 검사 및 데스크톱 로그/방문·태블릿 방문·모바일 로그/흐름/퍼널 이미지를 직접 확인했다. 페이지 로그/방문 렌더 검사는 FAIL/WARN 0개이며 기존 SYNC 퍼널의 공통 시트 하단 여백 WARN 1개는 보존했다. 모든 브라우저 검사는 운영 빌드와 합성 API 계약을 사용하며 실제 DB/운영 API 검증이 아니다. 상세 근거는 `pages-v24-frontend.log`, `pages-v24-dashboard-ui.log`, `pages-v24-main-ui.log`와 worktree의 ignored `output/playwright`, `.taste-skill`에 있다.

첫 프론트 단위 실행은 샌드박스의 하위 프로세스 `spawn EPERM`으로 차단됐다. 해당 로그를 `pages-v24-frontend-sandbox-blocked.log`로 남기고 실행 권한을 갖춘 환경에서 동일 검사를 통과했다. 제품 테스트 실패로 분류하지 않는다.

공통 완료 observer·main TAP 복구·신규 schema가 결합한 회귀를 확인하기 위해 핵심 통과 후 전체 백엔드 통합 수트를 한 번 실행했다. 10:18:44 KST에 7분 56초 실행을 마쳤으며 `BUILD SUCCESSFUL`과 JUnit XML에서 **총 240개 중 239개 통과·선택형 장애 주입 1개 건너뜀·실패/오류 0개**를 확인했다. 건너뛴 것은 `EventRehearsalIntegrationTest.pausedEphemeralDependenciesFailClosedAndRecoverWithoutDeletingData`이며 `TAGME_REHEARSAL_FAULTS=1` 선택 검사다. `.codex-log/pages-v24-full-backend.log`와 `pages-v24-full-summary.json`에 근거를 보존했다. 단위 103개는 앞 실행의 결과를 재사용했고 다시 실행했다고 표시하지 않는다. vendored skill 무결성·변경 Markdown 상대 링크·`git diff --check`도 통과했다.

새 `V24UpgradeIntegrationTest`는 빈 DB V1→V24, 데이터를 가진 V20에서 21/22/23/24 순차 적용, 모든 기존 Flyway 이력 필드/checksum 및 사용자·탭·복구·퍼널 행 보존, 구 퍼널 writer와 새 페이지 삽입, 재실행 멱등성, 원본 퍼널 V21+페이지 V23 이력의 안전한 거절을 검사한다. 기존 `V23UpgradeIntegrationTest`의 과거 경로는 원본 커밋에 보존하고 후보 경로 검사로 대체했다.

분석 통합 검사에 main 자동 TAP 복구와 P0 커밋 이후 수집의 결합 검사를 추가했다. 기존 탭 원본 이벤트·접수/인정 시각을 보존하면서 실제 복구 1회에 SERVER 성공 이벤트 1개만 남는지 확인한다. 앱의 분석 저장 장애가 업무 완료를 방해하지 않는 기존 검사도 유지한다.

## 남은 안전 조건과 이전 준비

로컬 유지 `tagme_fixtures`는 이전 읽기 전용 조사에서 V20까지만 적용됐고 퍼널/페이지 테이블과 세션 열이 없었다. 이번에는 다시 접속하거나 변경하지 않았다. 운영·공유 개발·스테이징·다른 개발자 PC의 기존 퍼널 V21/페이지 V23 적용 및 수동 SQL 여부는 **미확인**이다. 환경 관리자에게 [기존 읽기 전용 확인 요청](environment-migration-confirmation-request.md)을 전달해 version/script/checksum/success와 테이블·열 존재 및 수동 실행 여부를 확인해야 한다. 메시지를 전송하거나 해당 환경에 직접 접속하지 않았다. 격리 기술 검증은 통과했지만 유지 DB 안전성이 확정되지 않아 실제 #37 재번호·일반 Push는 계속 보류하며 별도 승인 전에는 진행하지 않는다.

오전 bundle(87 refs/필수 5커밋/검증 문서 11개)의 복원 검증 결과는 재사용했고 bundle SHA-256 `ed47ae1e519e1461b6213433a4c023ba0628ee4a040e840752ab2243312a57f9`는 유지됐다. 현재 접근 가능한 파일시스템은 C:뿐이며 연결된 USB/별도 드라이브가 없어 외부 복사를 수행하지 못했다. 오전 백업은 `C:\Users\SSAFY\Desktop\seoul\tagme-backups\20261118-am-094404`에 있다. USB 또는 승인된 비공개 외부 경로를 연결한 뒤 bundle·manifest·SHA256SUMS·별도 문서를 복사하고 SHA-256을 비교해야 한다. 임의 업로드하지 않았다. 원본 브랜치/필수 커밋/원본 worktree 청결 상태와 bundle 해시 확인 결과는 `.codex-log/pages-v24-protection-check.json`에 있다. 원격 main `f244e3c`와 #37 `1f565dc`/#39 `ecd75ee`도 읽기 전용 재확인에서 동일했다.

16:00 전체 백업에는 새 후보 브랜치/모든 worktree·refs, 이 문서와 추가 테스트, 원본 P0/P1/PR 브랜치, 오전 bundle과 기존 조사 문서, 새 `.codex-log/pages-v24-*` 요약·검증 근거를 포함해야 한다. 원시 로그/XML은 비밀정보·개인정보 검사 후 필요한 안전한 근거만 별도 보존한다. `node_modules` junction, 빌드 산출물과 비밀 설정을 Git bundle에 포함하지 않는다. 이 새 후보는 오전 bundle 생성 이후 작업이므로 오전 bundle만으로 복원되지 않는다.
