# TAG ME — 최신 통합본 개발 인수인계 (2026-11-10)

> **상태: 통합 검토용 / 배포 금지 / 병합 전 협의 필수**
> 대상: SSAFY 16기 TAG ME 협업 개발자·통합 담당자
> 기준: `anonymity-developer/connection_16`, `feat/network-graph` 기준 커밋 `86ad1dd`에서 파생된 **로컬 통합 작업본**
> 의도한 전달 방식: 별도 GitHub handoff 브랜치 + 검토용 **Draft PR** (PR 생성·브랜치 Push 자체는 배포나 병합이 아님).
> **검증 근거:** 개발자 로컬 실행 로그 및 독립 WORK 검토 보고(2026-11-10). 내용 중 ‘확인 필요’는 완료로 간주하지 않는다.

## 0. 처음 읽는 사람을 위한 핵심 요약

TAG ME는 SSAFY 16기 교육생이 층별 NFC를 통해 서로 연결되는 이벤트다. **SHOW ME는 최신 기획에서 NFC 없이 프로필 완료로 자동 지급**하며, 나머지 **SYNC ME / MAKE A WISH / FAST TAP / SAY 파이팅!** 4개만 NFC 해금 대상이다. 서버의 실제 완료·승인 상태가 SSAFY 글자 수집판과 시상의 원천 데이터여야 한다.

이번 통합본에는 인증·Mattermost, 네 미션 NFC/미션 체험, SHOW ME 자동 지급, 미션 UI, 동료 추천·연결 그래프, 소원 연못/동전 애니메이션, 콘텐츠 검수, 시상 후보·관리자 결과/내보내기, 테스트 및 운영 문서 변경이 모여 있다. **단, 로컬 통합본의 변경 내용이 모두 GitHub PR #11에 들어간 것은 아니다.**

**언니에게 요청:** 새로 구현하기 전 아래 파일·API를 비교해 **중복 개발 방지**, 운영상 결정할 사항 협의, 실제 Mattermost/물리 NFC/관리자 실사용 계정에서의 통합 테스트. 아직 배포·최종 수상자 확정은 하지 말 것.

## 1. 작업 기준과 버전 분리

| 구분 | 위치·기준 | 상태·주의 |
| --- | --- | --- |
| 최신 기능 통합본 | 별도 로컬 worktree `connection_16-integration-check`, `86ad1dd`에서 파생 | 최초 확인 시 `HEAD (no branch)` 및 **수정·미추적 파일 다수**. Git에 올라가기 전 새 파일까지 포함되어야 함 |
| UI 리뷰 PR #11 | https://github.com/anonymity-developer/connection_16/pull/11 | `eda-21:review/ssafy-ui-approval` → 당시 실제 base `main`; **최신 통합본 아님**. `3a951c2` 인증 테스트 수정까지 원격 반영 상태로 확인. 로컬 별도 두 파일 CSS·Windows 검사 수정은 스테이징까지 완료, 아직 커밋/Push 미확인 |
| WORK 코드 감사 | 2026-11-10 독립 점검 | 구형 SHOW ME 태그 `/me/profile` 호환성 패치 제안. **통합본에 미적용**. 앱/DB 시계 및 운영 시상 정책 미해결 |
| 실행 환경 | Java 21, Spring Boot, MySQL, Redis, React/Vite, Docker, Mattermost | Testcontainers는 로컬 임시 MySQL/Redis이고 실제 서비스 데이터 검증이 아님 |

**안전 규칙:** `main`, `feat/network-graph`, PR #11을 임의 덮어쓰지 않는다. `reset --hard`, `clean -fd`, force push, 자동 병합, 배포 금지. 코드를 GitHub로 보낼 때 작업 중인 언니 브랜치와 충돌을 리뷰한다.

## 2. 기능별 개발 의도 및 주요 파일

아래 경로는 `connection_16-integration-check` 로컬 작업 트리에서 관찰한 변경·신규 파일과 테스트 로그를 기준으로 작성했다. 각 파일의 **세부 클래스 내부 동작은 실제 diff/코드 리뷰로 최종 확인**한다.

### 2.1 인증 및 Mattermost

- `backend/src/main/java/com/tagme/auth/MattermostClient.java` — Mattermost 사용자 조회·발신 계정 세션/DM 연동. 기존 구현과 수정 diff를 비교해서 인증정보 노출 및 요청 중복 발송을 확인.
- `backend/src/main/java/com/tagme/auth/OtpMessage.java` — OTP 안내 메시지 관련 신규 코드.
- `frontend/src/pages/Auth.tsx`, `Account.tsx`, `frontend/src/pages/Tag.tsx` — 회원가입, 로그인, 비밀번호 변경/재설정, NFC 진입·로그인 후 복귀 UI.
- 관련 테스트: `backend/src/test/java/com/tagme/auth/MattermostSenderSessionTest.java`, `OtpMessageTest.java`, `frontend/scripts/check-auth-ui.mjs`, `check-auth-nfc-ui.mjs`.
- **설계 원칙:** Mattermost 계정으로 사용자 식별·OTP DM을 보내고, TAG ME 별도 비밀번호로 이후 로그인. 서버 세션 유휴 만료 4시간, NFC pending intent 15분(이력상 계약), CSRF·쿠키 유지, 무분별한 DM 재시도 방지.
- **주의:** 브라우저 모의 API·mock Mattermost 테스트는 실제 발신자 권한, DM 수신, 세션 종료, 실계정 승인 여부를 검증하지 않는다. 실제 MM 연동 작업 담당자와 별도 확인.

### 2.2 참여·SHOW ME 자동 지급·4개 NFC

- `backend/src/main/java/com/tagme/participation/ParticipationService.java` — 프로필 저장/완료·참여 관련 처리.
- `MissionCompletionService.java`, `ShowMeCompletionRule.java`, `ParticipationController.java` — 완료 증거·완료 전환·보상·SHOW ME 처리의 중심. **SHOW ME는 프로필 완료 시 자동 S 지급, NFC 요구하지 않는다는 최신 기획**을 기준으로 확인.
- `frontend/src/features/participation/api.ts`, `context.tsx`, `refresh.ts`; `frontend/src/pages/Profile.tsx`, `Mission.tsx`, `Home.tsx`, `Tag.tsx` — 서버 진행 상태와 프로필/태그/수집판 UI 연결.
- 테스트: `ShowMeAutomaticCompletionTest.java`, `ShowMeCompletionRuleTest.java`, `ParticipationIntegrationTest.java`, `frontend/tests/mission-plan.test.mjs`, `frontend/tests/ui-presentation.test.mjs`.
- 완료와 수집 글자 표시를 클라이언트 계산/미리보기만으로 확정하지 않는다. 중복 저장·새로고침·재로그인·동시 요청 시 **서버 idempotency**가 필요.
- **미해결:** WORK 감사에서 구형 SHOW ME 태그 진입의 `/me/profile` 응답 호환성 결함을 발견하고 최소 패치를 제공했으나 이 통합본에는 아직 반영·재검증되지 않음. 과거 코드/문서의 `SHOW ME NFC 필수` 문구는 최신 결정과 충돌하므로 폐기 전 검토.
- **기획 충돌 확인 필요:** 종전 기획에는 소개·관심사 1개 이상이 필수였지만, 최신 브라우저 계약에는 `닉네임만 입력 + 선택 항목 비움`을 허용하는 시나리오가 존재. **프로필 완료 및 S 지급의 최종 필수 조건을 운영 기획과 코드가 일치하도록 확인해야 함.**

### 2.3 미션별 화면과 서버 연결

| 미션 (표시명) | 미션 코드·진입 | 현재 의도 / 확인 포인트 |
| --- | --- | --- |
| SHOW ME | `SHOW_ME`, **NFC 없음** | 필수 프로필·동의 완료 후 서버가 S를 자동 확정. 사전등록/행사 OPEN·마감 경계, 재시도·중복 지급 점검 |
| SYNC ME | `SYNC_ME`, NFC | 설문→결과→`나와 통하는 동료 만나기` 추천 경로. 공통 관심사·유형·미방문 우선순위와 그래프는 별개 |
| MAKE A WISH | `MAKE_A_WISH`, NFC | 소원 제출→짧은 동전 투척 애니메이션·소원 연못. **승인 뒤** 수집/시상 반영. 미승인·반려·중복 제출 확인 |
| FAST TAP | `FAST_TAP`, NFC | 5초 준비 카운트다운 + 10초 게임. 서버 기록·슬롯/취소/재개·동시 요청·검토 필요 상태를 구분 |
| SAY 파이팅! | 기존 식별자 `SAY_SOMETHING`, NFC | 동료 응원 작성·검수·하트 등. **DB 식별자 임의 변경 금지** |

대표 프론트 파일: `frontend/src/features/results/SyncMission.tsx`, `TapMission.tsx`, `frontend/src/features/content/WishLake.tsx`, `WishToss.tsx`, `WishGallery.tsx`, `FountainArtwork.tsx`, `frontend/src/pages/Mission.tsx`, `frontend/src/components/MissionProgress.tsx`.

콘텐츠/검수 및 관계 UI: `frontend/src/features/content/ContentMission.tsx`, `WishComments.tsx`, `HeartControl.tsx`, `frontend/src/features/discovery/NetworkFigure.tsx`, `components.tsx`, `frontend/src/pages/Discovery*.tsx`. **소원 댓글/응원 하트/승인 상태**와 시상 집계 조건을 실제 API·DB 기준으로 확인한다.

### 2.4 관리자 운영·시상·행사 후 결과

- 백엔드 수정: `OperationsController.java`, `OperationsRepository.java`, `OperationsService.java`.
- 백엔드 신규: `AwardCalculator.java`, `AwardModels.java`, `PostEventController.java`, `PostEventExport.java`, `PostEventModels.java`, `PostEventRepository.java`, `PostEventService.java`.
- 프론트 신규: `frontend/src/features/operations/AwardsPanel.tsx`, `ResultsPanel.tsx`, `awards-api.ts`, `results-api.ts`, `results.css`, 및 `frontend/src/pages/Operations.tsx` 수정.
- 테스트/문서: `backend/src/test/java/com/tagme/integration/PostEventIntegrationTest.java`, `backend/src/test/java/com/tagme/operations/`, `frontend/tests/awards-candidates.test.mjs`, `post-event.test.mjs`, `docs/awards-candidates/`, `docs/post-event/`, `docs/operations-admin.md`.

**운영 원칙**

1. 참가자 자격: **5개 미션 전체 유효 완료**. 미승인 콘텐츠, 진행 중·반려, 임시 검토 기록은 확정 완료/보상으로 취급하지 않는다.
2. 시상 후보와 최종 수상자는 다르다. 관리자 검증 전 **잠정 후보를 자동 확정하거나 지급하지 말 것**.
3. **중복 수상은 1인 1개**, 충돌 시 SSAFY 글자 5개 선완료 순서 등 기존 확정 규칙을 검토. **중복 수상 배분 우선순위와 FAST TAP 검토 필요 기록 처리 방침은 미확정**.
4. 시상 후보(최초 완성상, 댓글왕, 인기 응원상, FAST TAP 챔피언 등)와 랜덤 참여상 포함 범위는 실제 구현과 운영 정책을 맞춘다. 랜덤 당첨 자동 추첨 엔드포인트가 있다고 가정하지 않는다.
5. 보고·내보내기는 관리자 권한, no-store, 개인정보 최소화, 시점 고정 여부 검증. `PostEventExport`는 **CSV 관련 테스트 근거가 있으므로 `.xlsx` 산출을 임의 보장하지 않음**.
6. 프론트에 집계 수치가 보이더라도 서버 DB/관리자 승인 상태만 최종 기준으로 사용한다.

### 2.5 UI·디자인 및 개발용 체험

- 홈/수집판: `frontend/src/pages/Home.tsx`, `frontend/src/components/LetterSticker.tsx`, `EventArtwork.tsx`, `WinterDecor.tsx`, `frontend/src/styles/friendly.css`, `participant.css`, `polish.css`, `account.css`.
- 각 미션 이미지: `frontend/public/images/` 신규 추가. 누락 시 미션 페이지/수집판에 빈 이미지가 나타날 수 있음.
- 개발용 체험: `frontend/experience.html`, `frontend/src/dev/ExperienceApp.tsx`, `FountainExperience.tsx`, `WishLakePreview.tsx`, `experience-store.ts`, `experience.css`.
- 체험 페이지는 **개발용 시뮬레이터**다. 브라우저 검사에서 application API 요청 없이 PASS였으며, 이를 실계정·실NFC·실관리자 승인 검증으로 해석하지 않는다. 프로덕션 번들에 우연히 포함되는지 별도로 확인.
- UI 시나리오: 프로필/수집판/네 미션 진입, 360·390·430px 모바일, 태블릿, PC, 로그인 후 복귀, 장시간 로딩·API 실패, 키보드 포커스/스크린 리더, 동전 애니메이션 reduced-motion.

## 3. 실행 검증 — 사실과 한계

### 3.1 최신 통합본 (`connection_16-integration-check`)

| 실행 검증 | 결과 / 근거 | 한계 |
| --- | --- | --- |
| `npm --prefix .../frontend run build` | PASS | 실제 운영 서버 응답까지 검증하진 않음 |
| `npm --prefix .../frontend run lint` | PASS | 〃 |
| `npm --prefix .../frontend run test:unit` | **94/94 PASS** (2026-11-10 로그) | 이후 WORK 패치 95/95는 별도 사본 검증이며, 미적용 통합본과 혼동 금지 |
| `node frontend/scripts/check-experience-ui.mjs` (repo root에서) | **PASS development browser interactions; no application API requests** | 체험 전용 |
| `cd backend && ./gradlew test --no-daemon` | **BUILD SUCCESSFUL** | JUnit `integration` 태그 제외 |
| `cd backend && ./gradlew integrationTest --rerun-tasks --no-daemon` | **124개 중 123 PASS / 1 SKIP / 0 FAIL / 0 ERROR**, BUILD SUCCESSFUL (2026-11-10) | Testcontainers 임시 DB/Redis 기반, 실제 MM/운영 DB 아님 |

DB 통합 첫 실행에서 `EventRehearsalIntegrationTest`의 `GET /csrf`가 401: 일반 사용자 가입 세션을 유지한 채 DB 역할을 ADMIN으로 변경했기 때문. 테스트의 `admin.login("control")` 호출 전에 **테스트 클라이언트의** `admin.cookies.clear(); admin.csrf=null;`을 추가한 뒤 단독 리허설 및 전체 DB 통합 재실행 PASS. **운영 인증 로직/보안 필터는 수정하지 않음.** 변경 위치: `backend/src/test/java/com/tagme/integration/EventRehearsalIntegrationTest.java`.

건너뛴 테스트 `pausedEphemeralDependenciesFailClosedAndRecoverWithoutDeletingData()`는 `@EnabledIfEnvironmentVariable(named="TAGME_REHEARSAL_FAULTS", matches="1")`로 의도적 제외. Docker의 Redis와 MySQL 컨테이너를 `pause/unpause`하는 장애 복구 리허설이므로, **격리된 개발·테스트 환경에서만 계획적으로** 실행. 운영 Docker/공유 DB에 실행 금지.

통합 테스트 종료 과정에 Redis connection timeout 및 Hikari/MySQL 접속 종료 경고가 반복 관찰되었다. 전체 테스트는 통과했지만 **종료 순서/스케줄러 정리 품질은 후속 점검**이 필요하다.

### 3.2 별도 PR #11 (`connection_16`)

로컬 PR용 worktree `fix/tagme-pr11-ci`에서 `friendly.css`의 `box-shadow: none` 수정, Windows 테스트 러너의 `.sh` 대신 Chromium `chrome.exe` 실행 경로 분기 수정 후 다음이 PASS: lint, build, unit **21/21**, 인증 브라우저 **5/5**, 참여 브라우저 **6/6**, 디자인 검사 **0 FAIL / 2 WARN**, `git diff --check`. 디자인 WARN은 데스크톱 카드 행 높이 차이와 모바일 크림색 배경. **이 2개 파일은 사용자 로컬에서 stage만 완료한 상태이며 커밋/Push 및 GitHub CI 최종 검증 미확인. 통합본에 무작정 적용·혼합하지 말 것.**

## 4. 오픈 전 MUST FIX / 검증 대기

| 우선 | 항목 | 인수인계 후 확인 방법 / 책임 제안 |
| --- | --- | --- |
| P0 | **구형 SHOW ME 태그 경로 호환성 패치 미적용** | WORK의 최소 패치와 현재 코드 diff 확인 → 기존 SHOW ME NFC 없는 정책을 깨지 않는지 재검증 |
| P0 | **완료 시각 일관성** (SHOW ME 앱 시계 vs DB 시간) | DB 표준 시각 기준 결정 → 시계 편차·행사 OPEN/마감 경계·동시 완료 회귀 테스트 |
| P0 | **프로필 완료 필수 조건** 확인 | 종전 `소개·관심사 ≥1` vs 최신 `닉네임만 허용` 계약 충돌을 기획 담당과 확정 → 지급 테스트 일치 |
| P0 | **시상·FAST TAP 운영 판정** | 중복 수상 배분, 검토 필요 기록, 승인/무효 처리 규칙 및 최종 관리자 수동 확정 절차 문서화 |
| P0 | **실제 MM 및 모바일 NFC** | 승인된 발신 계정/본인 수신 계정으로 1회 DM, OTP, 4개 태그 위치별 URL, 로그인 복귀, 401/재인증, 물리 NFC 테스트 |
| P1 | PR #11 CI와 통합 범위 정리 | 기존 PR base는 당시 `main`·81개 파일. 별도 handoff PR에 중복 포함되는지 팀 리뷰. 강제 병합·base 임의 변경 금지 |
| P1 | Redis/MySQL 종료 시 경고 | Testcontainers 종료 후 스케줄러 정리 순서가 회귀/운영에 영향 주는지 확인 |
| P1 | UI 디자인 경고/접근성 | 렌더 스크린샷 실제 점검, 초점·키보드, 카드 높이와 색상 결정을 디자인 담당과 협의 |
| P1 | 운영 설정/환경 | `NOT_CONFIGURED`, 참여 안내, 승인 정책, 관리 권한, 행사 기간, 재시도 제한, 시상 CSV·개인정보 보관 확인 |

**GO/NO-GO:** 테스트 다수가 통과해도 실제 MM·NFC, 운영 설정, 시계 정합성과 시상 정책이 미해결이면 **NO-GO 유지**. 이 문서는 인수인계를 위한 코드 전달이지 서비스 오픈 승인서가 아님.

## 5. 협업·리뷰 권장 순서 (배포 없음)

1. **현재 상태 보호:** `connection_16-integration-check`의 수정 파일과 `??` 신규 파일을 모두 포함해 **별도 로컬 브랜치**에 안전하게 보존. 변경/비밀값 스캔 및 `git diff --check`/`git diff --cached --check`.
2. **인수인계 문서 포함:** 이 문서를 `docs/TAGME_INTEGRATION_HANDOFF_20261110.md`로 추가. 과거 `docs/mm-ui-handoff.md`는 구 UI/구 기준의 이력 자료로 유지하고 이번 문서를 **최신 통합 설명**으로 링크.
3. **검토용 PR:** 작업자와 합의한 기준 브랜치를 base로 Draft PR 생성. 현재 `feat/network-graph`가 존재함을 확인했으나 PR의 실제 diff·중복·충돌을 확인하고 base를 결정. **PR을 만든다고 배포/병합하는 것은 아님**.
4. **언니와 겹치는 범위 식별:** 언니의 최신 브랜치/커밋과 인증 MM·운영 콘솔·관리자 코드 diff를 나란히 확인. 각 파일/기능별 담당을 확정하고 충돌 시 수동으로 검토.
5. **검토 우선순위:** P0 위험 해결 → DB 통합 재시험 → 실제 MM/NFC 테스트 → 운영 정책/배포 승인 별도 절차.

## 6. 언니에게 요청하는 확인 사항

- 언니가 현재 작업 중인 **브랜치 및 기준 커밋**, 변경 파일 범위는 무엇인가? 충돌 가능성이 큰 `MattermostClient`, `Operations*`, `Participation*`, `Profile`, `Auth`부터 비교.
- **관리자 수상 집계·결과 조회·내보내기**는 통합본에 이미 구현 흔적과 테스트가 있으니 중복 개발하지 않고 기능·정확도 리뷰를 부탁.
- SHOW ME는 **NFC 없이 프로필 완료** 정책을 유지할지, 프로필 필수 입력을 어디까지로 할지 최종 확인.
- 서버가 사용할 **완료 시각의 단일 기준**과 경계 조건을 결정하고 테스트 보강.
- 랜덤 참여상, 중복 수상 우선순위, FAST TAP 재검토 대상 확정 절차를 운영자와 합의.
- **실제 MM DM·발신 계정 세션 정리·NFC 4종** 현장 테스트의 담당/일정을 공유.

## 7. 개인정보·보안 주의

- GitHub에 `.env`, 접근 토큰, MM 발신 계정 비밀번호, 실제 OTP, 원본 참여자 데이터, 운영 DB 백업, NFC의 원본 비밀 토큰 번들, 개인 연락처를 올리지 않는다. `.gitignore`만 신뢰하지 말고 **staged 파일을 직접 검토**한다.
- `docs/`와 새 `??` 파일도 스캔한다. ZIP/패치에 포함된 진짜 비밀값은 손으로 제거하고 필요하면 키를 회전한다.
- 실운영 데이터 삭제/DB 초기화/Redis `FLUSHDB`/컨테이너 중지·재시작이 포함된 테스트를 **공유 환경에서 실행하지 않는다.** 로컬 Testcontainers 격리 여부를 확인한다.
- 이 문서는 구현 상태·검증 한계를 기록한 자료다. 기존 워크트리의 실제 최신 코드와 차이가 발견되면 코드를 근거로 수정해 기록한다.

---

**작성 범위:** 사용자 제공 로컬 파일 현황·명령 출력 및 WORK 검토 요약 기준. 현재 로컬의 모든 파일 내용을 원격에서 완전 열람한 것은 아니므로, 브랜치에 올리기 전 언니와 실제 diff 및 테스트 커버리지를 대조해야 한다.
