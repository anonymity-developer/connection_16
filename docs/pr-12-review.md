# PR #12 통합 리뷰 — 2026-11-10

## 수정 및 재검증

사용자 승인으로 `review/pr-12-integration`에서 문제를 수정했다. 작성자의 후속 `a5d2911`도 병합했으며 원본 fork 브랜치는 변경하지 않았다. **아래 초기 리뷰의 병합 차단 사유는 수정·재검증했고, main 통합 PR의 CI 확인 후 병합한다.** 관리자 `feat/admin-page`는 계속 `64a0ad7`에 보존한다.

- Docker 자료 누락은 작성자의 frontend 내부 JSON 사본을 채택했다. 문서의 검토용 원본과 일치하는지 단위 테스트를 추가했고 운영 빌드에는 체험 entry를 포함하지 않는다.
- 기존 SHOW ME 태그·로그인/가입 뒤 resume는 `/me/profile`로만 이동한다. 다른 네 미션의 잘못된 프로필 경로, SHOW ME의 이전 미션 경로, 외부 URL은 계속 거부한다.
- SHOW ME 프로필 접수·자동 해금·지급 검증은 DB 시계를 사용한다. 지급 직전 한 DB 시각으로 OPEN 구간을 검증하고 evidence/mission 완료에 같은 값을 저장한다. 사전 등록 소급·중복 지급은 하지 않는다.
- DB 세션의 시간을 JVM 시계와 다르게 고정한 MySQL 테스트로 개장 포함·마감 제외·처리 중 마감 전체 롤백을 확인했다. 시스템/공유 DB 시계는 변경하지 않았다.
- 현재 UI에 맞춰 인증 오류의 alert, 자동 SHOW ME, 중립 수집판, 소원 동전→댓글, 로그인 안내 및 Windows 렌더 실행기를 수정했다. 시상 select의 label을 명시적으로 연결했다.
- FAST TAP에 덮어쓰인 `touch-action: manipulation`을 `none`으로 복구해 탭 영역의 모바일 스크롤을 차단한다. 일반 버튼 그림자에 대한 렌더 FAIL도 수정했다.
- CI에는 기존 검사 외에 NFC 복귀·시상·행사 결과 브라우저 검사를 추가했다. 테스트를 삭제하거나 보안·멱등성·횟수 검사를 완화하지 않았다.

로컬 검증은 프론트 lint/build, 단위 95개, 일반/인증/참여/결과/콘텐츠/반응/탐색/운영/NFC/시상/행사 결과의 11개 브라우저 묶음이 통과했다. 백엔드는 단위 84개와 bootJar가 통과했다. 전체 통합 127개 중 의도적 장애 주입 1개는 skip이며, 새 테스트의 JDBC 형변환 오류 1개를 수정한 후 해당 참여 통합 19개가 전부 통과했다. 그 외 전체 검사에서 실패는 없었다. 최종 전체 회귀는 통합 PR CI로 확인한다.

분리한 `tagme-pr12-ci` 스택은 두 이미지 빌드·네 컨테이너 healthy·웹→API metadata smoke를 통과했다. Windows에는 Python이 없어 `smoke.sh`의 동일한 상태/서비스/5개 코드/SSAFY 글자 검증을 Node 24로 실행했다. 기존 관리자·참가자 컨테이너/DB와 `.env`는 변경하지 않았다. Compose 두 구성, YAML 5개와 OpenAPI 참조 39개, 문서 링크·공백·스킬 무결성도 확인했다.

화면은 새로 디자인하지 않고 기존 TAG ME 스티커·색상·배치를 유지했다. 1440/768/390px 홈·프로필·운영 렌더를 검사하고 이미지를 확인했다. Taste 결과는 홈 0 FAIL/1 WARN, 프로필 0 FAIL/2 WARN, 운영 0 FAIL/0 WARN이다. 홈의 긴 미션 목록은 스크롤로 제공하고, 프로필 폼 옆 짧은 실시간 미리보기와 첫 S의 노란 안내 영역은 현재 기능 구분을 위해 유지한다. 정적 경고 30개는 `box-shadow: none` 선언과 게임 버튼/동전/툴팁 효과, OTP 시계의 앞자리 0, 투척 물리 연출의 ease-in을 포함하며 정적 검사만으로 실제 화면 실패로 간주하지 않았다. 모든 측정 FAIL은 해결했다.

실제 Mattermost DM·실물 NFC·운영 DB 및 수상 배분 확정은 이번 코드 병합 검증과 별개로 남아 있다. 잠정 후보를 최종 수상자로 전환하거나 서비스를 배포하지 않는다.

## 판정과 보존 상태

다음은 `88caf86`에 대한 **초기 리뷰 당시 기록**이다. 당시에는 Git 텍스트 충돌이 없어도 Docker 빌드 실패·실제 태그 이동 오류·시각 정합성 문제로 병합을 보류했다. 이후 수정과 검증 상태는 위 절을 따른다.

- 대상: [PR #12](https://github.com/anonymity-developer/connection_16/pull/12), `eda-21:handoff/tagme-integration-20261110`의 `88caf86d233990f4b19048ba3ab840888524ba8d`.
- PR의 실제 base는 `feat/network-graph`의 `86ad1dd87a64a1879766dc7682ff8eafc676e68d`다. `main` 대상 PR이 아니다.
- PR 자체는 186개 파일 변경이며, 선행 `feat/network-graph`까지 `main`에 포함하면 214개 파일 변경이다. 메인 반영 시 선행 인증·검수·그래프 변경도 함께 리뷰해야 한다.
- 원래 폴더 `connection_16` 안의 `review/pr-12-integration`에서 리뷰했다. 추가 작업 폴더나 워크트리를 만들지 않았다.
- 관리자 `feat/admin-page`는 `64a0ad7a533c6ab0ed49ce171b5c939905f00fcb`, `main`과 `origin/main`은 `5a635997f4f8ce2499b91bd7061f64b1970ffdd7`에 보존했다. 기존 `pr-review`도 변경하지 않았다.
- `.env`, `.env.pr-review`, 기존 관리자/PR 컨테이너와 영속 DB를 변경하지 않았다. 리뷰는 실제 Mattermost DM이나 공식 참가 기록을 만들지 않았다.

## 병합 전에 수정할 사항

### 1. [P1] Docker가 빌드할 수 있는 범위에 개발 체험용 JSON을 둔다

위치: [experience-store.ts](../frontend/src/dev/experience-store.ts) 첫 줄, [Dockerfile](../frontend/Dockerfile), [Compose](../compose.yml)의 web build context.

`experience-store.ts`가 `../../../docs/sync-questionnaire.proposal.json`을 import한다. 로컬 전체 저장소에는 이 파일이 있지만 web의 Docker context는 `./frontend`이므로 컨테이너에는 없다. TypeScript가 `src` 전체를 검사하여 개발 전용 모듈이어도 `TS2307` 및 후속 타입 오류로 실패한다. 운영 번들에서 개발 화면을 제외하는 것만으로 해결되지 않는다.

- 재현: 저장소 루트에서 `docker build -t tagme-pr12-review-web:local ./frontend` → 실패.
- [Docker CI](https://github.com/anonymity-developer/connection_16/actions/runs/42740436619/job/129579853712)도 같은 JSON 누락으로 실패했다. API 이미지 빌드는 통과했고 web 빌드에서 중단했다.
- 체험 자료를 frontend 내부의 명시적인 개발 fixture로 두거나, 빌드 경계를 일관되게 수정해야 한다. 수정 뒤 Docker web 빌드와 전체 스택 smoke를 다시 수행한다.

### 2. [P1] 기존 SHOW ME 태그의 프로필 이동 응답을 허용한다

위치: [Tag.tsx](../frontend/src/pages/Tag.tsx) 26행, [ParticipationService.java](../backend/src/main/java/com/tagme/participation/ParticipationService.java) 115~117행.

서버는 구형 SHOW ME 태그에 `missionCode=SHOW_ME, route=/me/profile`을 반환하지만, 프론트는 모든 응답을 `/missions/{slug}`와 비교한다. 그 결과 정상 서버 응답에도 `이동할 미션을 확인하지 못했어요.`를 표시하고 태그 페이지에 머문다. 로그인 뒤 `/t/resume`도 같은 응답을 받으면 같은 문제가 발생한다.

- 운영 빌드 + 실제 Playwright 브라우저에서 서버 계약 그대로의 모의 응답을 사용하여 390px와 1440px 모두 재현했다. 실제 태그 토큰이나 계정은 사용하지 않았다.
- SHOW ME만 `/me/profile`, 나머지 네 미션은 각 미션 경로로 검증하도록 수정하고 직접 진입·로그인/가입 후 resume 회귀 테스트를 추가한다. 임의 외부 URL 이동을 허용하지 않는다.

### 3. [P1] SHOW ME 완료 근거와 완료 원장의 시각 기준을 통일한다

위치: [MissionCompletionService.java](../backend/src/main/java/com/tagme/participation/MissionCompletionService.java) 34~35·55행, [ParticipationRepository.java](../backend/src/main/java/com/tagme/participation/ParticipationRepository.java) 161~168행, [OperationsRepository.java](../backend/src/main/java/com/tagme/operations/OperationsRepository.java) 73~75행.

새 SHOW ME 경로는 `accepted_received_at`을 앱의 `Instant.now()`로 강제 설정한다. 행사 OPEN 판단과 `mission_progress.completed_at`은 DB의 `UTC_TIMESTAMP(6)`를 사용한다. 시상 쿼리는 `completed_at >= accepted_received_at` 및 인정 시각의 행사 기간을 요구한다. 앱 시계가 DB보다 앞서면 정상 지급한 SHOW ME가 무효 근거가 되어 전체 5종 완료자를 후보에서 제외할 수 있고, 마감 경계에서는 DB가 OPEN인데 인정 시각은 마감 이후일 수 있다.

이는 코드 경로와 SQL 조건을 대조한 결과다. 이번 리뷰에서 시스템 시계를 변경하거나 실제 시계 편차를 주입하지는 않았다. 기존 테스트 통과를 편차 검증으로 간주하지 않는다. DB 기준의 명시적인 처리 시각을 사용하고, 사전 등록·동시 처리·앱/DB 시계 차이·OPEN/마감 경계에서 지급과 시상 유효성이 함께 유지되는지 검사한다.

## CI와 검증 코드의 추가 문제

- [Frontend CI](https://github.com/anonymity-developer/connection_16/actions/runs/42740436619/job/129579853925)는 `check-ui.mjs:58`의 `.collection-count` 대기에서 30초 timeout으로 실패했다. 현재 홈과 이전 테스트 계약을 맞춰야 한다. 이후 인증·참여·미션·검수·탐색·Taste 단계는 실행되지 않았다. CI 전체를 통과했다고 볼 수 없다.
- 신규 `check-awards-ui.mjs:27`도 로컬에서 `getByLabel('시상 부문', { exact: true })` timeout으로 실패했다. 별도 브라우저 점검에서는 후보 화면과 combobox 1개가 정상 로드되지만 정확한 label 선택자 결과는 0개였다. label에 option 텍스트까지 포함되는 구조이므로 명확한 레이블 연결이나 테스트 선택자를 정리해야 한다. 후보 API 장애로 단정하지 않는다.
- Windows의 원래 `npm run test:unit`은 여러 Vite 테스트가 같은 캐시를 동시에 정리하여 82 통과/2 파일 실패(EPERM)했다. `node --test --test-concurrency=1 tests/*.test.mjs ../scripts/*.test.mjs`로 실행하면 94/94 통과했다. Linux CI의 기본 단위 테스트는 통과했다.
- Taste 정적 검사 `node scripts/taste.mjs lint --app frontend/src`는 1 FAIL/30 WARN이다. FAIL은 개발 전용 `FountainExperience.tsx:28`의 장식 기호이며 운영 번들 노출과 구분해야 한다. 28개 shadow, 시간 표시의 `padStart` 1개, `ease-in` 1개 경고는 실제 용도를 따로 판정해야 한다. 렌더 검사·스크린샷 전체 시각 검수 통과는 주장하지 않는다.

## 실행한 검증

| 검사 | 결과 |
| --- | --- |
| 관리자/main 참조와 원래 작업 경로 보존 | 확인 |
| `git merge-tree --write-tree --name-only main origin/pr-12` | 충돌 없음, 실제 merge 안 함 |
| 프론트 `npm run lint` | 통과 |
| 프론트 단위 테스트 | 기본 Windows 실행 캐시 충돌; 순차 실행 94/94 통과 |
| 로컬 `npm run build` | 통과 |
| Docker web build | JSON 누락으로 실패, GitHub CI와 동일 |
| `npm run test:auth-nfc-ui` | 네 NFC의 로그인/가입 후 복귀 8종 + 기존 세션/만료/재스캔/로그아웃 4종 통과 |
| SHOW ME 기존 태그 브라우저 재현 | 390/1440px에서 오류 재현 |
| `node scripts/check-awards-ui.mjs` | 정확한 레이블 선택자 timeout; 별도 모의 화면 로드로 원인 확인 |
| `node scripts/check-post-event-ui.mjs` | 6개 폭, 권한, 제한 이력, 내보내기, 스냅샷 변경, 권한 회수 후 정리 통과 |
| 개발/운영 Compose `config --quiet` | 둘 다 통과 |
| 스킬 원본 무결성 검사 | 통과 |
| 백엔드 단위·Testcontainers·bootJar | 이번 로컬 재실행 안 함. 해당 PR의 GitHub Backend job은 성공 |
| doctor 환경 점검 | Node 24/Docker/Chrome 확인. PATH Java는 8, gh 미인증. npm 탐지는 Windows에서 false이나 실제 npm 검사 실행 성공 |

로컬 JDK 21 재구성이나 외부 권한 변경은 하지 않았다. 기존 DB를 건드리는 전체 스택 실행은 Docker 빌드 실패 단계에서 진행하지 않았다. 테스트 데이터는 브라우저 요청 가로채기/메모리 응답이며 운영 기록이 아니다.

## 기능 합의와 후속 순서

SHOW ME의 NFC 제거·자동 지급, 소개/관심사의 보상 필수 조건은 기존 main 계약과 다르다. PR에서는 닉네임만으로 프로필 저장은 가능하지만 첫 S에는 소개·관심사도 요구한다. [인수인계](TAGME_INTEGRATION_HANDOFF_20261110.md)에 적힌 기획 충돌을 실제 결정으로 정리해야 한다. 실물 NFC·실제 Mattermost·수상 배분 정책 미검증은 배포/운영 준비와 구분한다.

먼저 위 결함과 CI를 수정·재검증하고, PR base와 main에 포함할 선행 변경 범위를 정리한다. 이상이 해소된 커밋을 대상으로 main에 병합한 뒤, 보존한 `feat/admin-page`에 최신 main을 통합하고 관리자 작업을 이어간다. 현재 관리자 브랜치에 리뷰 PR 코드를 섞지 않았다. 기존 관리자 순위는 운영 현황이고 PR의 시상 후보와 지표·동점 기준이 달라 별도 유지해야 한다.
