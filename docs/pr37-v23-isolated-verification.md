# PR #37 V23 후보 — 격리 통합 검증

상태: 2026-11-17 최종 전체 격리 검증 통과. 이 문서는 원본 PR 재번호/Push 승인이 아니다.

## 범위와 보존

- main `f244e3c`와 #37 `1f565dc`만 통합했다. #39 `ecd75ee`와 P0 `8cdebc6`/P1 `c8451b5`는 포함하지 않았다.
- 임시 브랜치: `review/pr37-v23-isolated-20261117`.
- worktree: 저장소 루트의 `.codex-log/integration-pr37-v23`. 기존 브랜치·원래 검증 산출물과 별도이며 결과 확인 전까지 보존한다.
- 원본 #37/#39/P0/P1/main 브랜치는 변경하지 않는다. 이번 검증은 Push·원격 CI·PR 병합·배포를 수행하지 않는다.
- DB는 테스트가 생성하고 닫는 일회용 MySQL 8.4/Redis Testcontainers뿐이다. 유지 fixture·운영·공유 DB에 접속하지 않는다. 이미 확인한 로컬 fixture V20 결과는 재사용한다.

## 마이그레이션 후보와 정적 검사

| 버전 | 후보 파일 | 처리 |
| --- | --- | --- |
| V1–V20 | main의 기존 파일 | 내용 보존 |
| V21 | `V21__tap_recovery_evidence.sql` | main 내용 보존 |
| V22 | `V22__system_tap_recovery.sql` | main 내용 보존 |
| V23 | `V23__add_mission_funnel_events.sql` | #37 V21의 SQL 바이트 그대로, 임시 worktree에서 파일명만 조정 |

원본/후보 퍼널 SQL SHA-256은 둘 다 `6d13ac55946ae04e6d4554907cc8414763dbd209644c42726de5a5110da2d27e`다. main의 모든 migration 파일도 원본과 비교해 보존을 확인했다. V1–V23의 중복/누락 번호와 CREATE TABLE 이름 중복은 없다. 실제 인덱스·FK·CHECK 검증은 아래 일회용 DB 검사에서 수행한다. P0 페이지 SQL/V24는 포함하거나 확정하지 않는다.

## 충돌 해결 시뮬레이션

충돌은 `frontend/src/pages/AdminConsole.tsx`, `docs/data-model.md`, `docs/erd.md`, `docs/progress.md`의 네 파일이다.

관리자 화면은 최신 main을 기반으로 #37의 FunnelPanel import·메뉴·분기만 추가했다. main의 TapSupport·순위 통합·기존 경로 처리는 보존했다. 문서는 TAP V21/V22와 퍼널 V23 설명을 모두 보존했다. main의 `ResultsService`, `ParticipationRepository`, Gradle UTC 설정과 관리자 TAP 검사 목록은 그대로다. #37의 퍼널 서비스·컨트롤러·화면·SYNC 수집 로직도 원래 내용과 같다. API/화면/미션 규칙을 다시 구현하지 않았다.

## 검증과 실패 기록

추가한 `FunnelV23MigrationIntegrationTest`는 네 일회용 DB 시나리오를 검증한다.

1. 빈 DB의 V1→V23, Flyway validate, 최종 스키마·집계 인덱스·사용자 FK·중복 키·단계/세대 CHECK.
2. V20에 사용자/탭 기록을 넣고 V21→V22→V23을 단계별 적용. TAP 복구 행의 V22 전후 보존, 기존 사용자/탭/복구 행과 이전 Flyway 이력의 모든 필드·checksum 보존, 최종 validate와 퍼널 쓰기 확인.
3. 옛 퍼널 V21이 적용된 별도 DB에서 새 후보의 validate/migrate 거절. repair 없이 옛 이력과 이벤트가 보존되는지 확인.
4. 두 V21 파일이 있는 후보가 사용자/업무 테이블 생성 전에 거절되는지 확인.

첫 전체 실행은 단위 102개 통과, 통합 총 222개 중 220개 통과·1개 실패·선택형 1개 건너뜀이었다. 추가한 fresh DB CHECK 단언에서 `DataIntegrityViolationException`을 기대했으나 MySQL 3819가 Spring `UncategorizedSQLException`으로 감싸져 실패했다. 실제 `ck_funnel_step` 거절은 발생했다. 추가 테스트만 실제 JDBC 오류 코드 3819와 제약 이름까지 확인하도록 보정했고 SQL/기능/기존 검사는 바꾸지 않았다. 첫 Gradle 로그와 해당 XML/집계는 저장소 루트 `.codex-log/pr37-v23-isolated-backend.log`, `pr37-v23-first-migration-result.xml`, `pr37-v23-first-summary.json`에 보존했다.

최종 `test integrationTest bootJar`는 Java 21/UTC에서 `BUILD SUCCESSFUL in 6m 53s`다. JUnit XML 기준 **단위 102개 통과, 통합 총 222개 중 221개 통과·선택형 1개 건너뜀·실패/오류 0개**, bootJar 성공이다. 신규 migration 검사 4개가 모두 통과했고 기존 퍼널을 포함한 `ResultsIntegrationTest` 23개와 `FunnelServiceTest` 2개도 통과했다. main의 ResultsIntegrationTest 15개와 #37의 퍼널 추가 8개를 보존했다. #37 기반의 옛 동일 밀리초 INVALID 검사 1개는 main에서 이미 승인된 동일 밀리초 허용/복구 검사로 대체된 상태를 유지했다. 옛 TAP 정책을 되살리거나 main 검사를 제거하지 않았으며 main 대비 이 파일의 차이는 #37의 퍼널 검사 추가뿐이다. 선택형 건너뜀은 기존 `EventRehearsalIntegrationTest.pausedEphemeralDependenciesFailClosedAndRecoverWithoutDeletingData`이며 `TAGME_REHEARSAL_FAULTS=1` 의존성 중단/복구 검사다. 통과로 세지 않는다.

빈 DB V1→V23과 V20→V21→V22→V23 모두 성공했고 각 단계의 validate도 통과했다. Flyway가 보고하는 24 migrations는 versioned 23개와 repeatable 1개로 V24가 포함됐다는 뜻이 아니다. 최종 Gradle 로그는 저장소 루트 `.codex-log/pr37-v23-isolated-backend-final.log`, 집계는 `pr37-v23-final-summary.json`, 상세 XML은 이 worktree `backend/build/test-results/`에 있다. SQL/정적 감사 결과는 `.codex-log/pr37-v23-static-audit.json`에 있다. 후보 JAR의 migration/클래스 목록과 fixture 코드 제외 검사도 통과했다. 변경 문서 상대 링크와 diff 검사도 통과했다.

프론트는 lint·단위 114개·운영 빌드·관리자/초기화/TAP 지원·게임·NFC/미션 흐름 브라우저 검사를 통과했다. 360/390/430/768/1440px 퍼널 스크린샷을 확인했고 기존 스타일/문구를 유지했다. 소스 검사 FAIL/WARN 0개, 렌더 검사 FAIL 0개·공통 시트 하단 여백 WARN 1개다. 브라우저는 합성 API 응답이며 실제 외부 연동/운영 DB 검증은 아니다.

## 외부 환경 확인과 승인 판단

이미 조사한 공식 릴리스/CI·로컬 fixture 결과를 반복 조사하지 않았다. 남은 운영·공유 개발·스테이징·다른 PC와 수동 SQL 여부는 [관리자 요청문](environment-migration-confirmation-request.md)으로 담당자에게 읽기 전용 확인을 요청해야 한다. 초안만 작성하며 에이전트가 발송하거나 해당 DB에 접속하지 않는다.

격리 검증 통과로 깨끗한/main 이력의 DB에서 후보가 동작한다는 기술적 조건은 충족됐다. 다만 실제 #37 재번호·일반 Push의 무조건 승인은 아직 권고하지 않는다. 옛 퍼널 V21이 적용된 별도 DB가 실제로 거절되는 음성 검사도 통과했다. 옛 퍼널 V21 또는 옛 페이지 V23을 이미 적용한 유지 DB에는 파일명만 바꾸어 적용할 수 없다. 해당 환경 확인과 사용자의 명시적 승인 전에는 실제 #37 재번호·일반 Push를 진행하지 않는다. main V21/V22나 Flyway 이력·checksum을 고쳐 통과시키지 않는다.

## P0 V23→V24 후속 영향

V23이 퍼널에 사용되면 P0 페이지 SQL은 후속 V24 후보로 옮겨야 한다. 퍼널 테이블이 먼저 존재해야 세션 열을 추가할 수 있다. 이번에는 P0 원본과 SQL을 수정하거나 V24를 확정하지 않았다.

기존 P0 `V23UpgradeIntegrationTest`의 옛 퍼널 V21→옛 페이지 V23 검증은 그대로 보존한다. 후속에는 새 퍼널 V23 데이터를 가진 DB의 V23→V24, 빈 DB V1→V24, 기존 이력/행/수집 시작 시각/nullable 세션 열·구 writer 호환성을 별도로 검증해야 한다. 관리자 메뉴/라우팅·문서와 main TAP 복구·인정 시각 규칙/완료 observer도 함께 회귀 검증한다. #39가 통합된 뒤에는 동료 전환 메뉴도 보존해야 한다.

롤백은 main V21/V22 이력과 호환되는 코드 이미지로 제한하고, 적용한 스키마/이력은 남긴다. 옛 퍼널 V21/페이지 V23을 싣는 이미지를 새 버전 이력의 DB에 그대로 연결하는 롤백은 승인된 호환성 검증 없이 사용하지 않는다.
