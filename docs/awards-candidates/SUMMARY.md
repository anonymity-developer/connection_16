# 시상 후보 후속 — 구현 완료·통합 검증 대기

2026-11-08 KST. 격리 worktree에서 86ad1dd + UI v5 후속 + final-over-v5-followup.patch + missions-over-final-integration.patch를 재현했다. 사용자의 Windows 폴더를 수정하거나 두 선행 패치가 적용됐다고 가정하지 않았다.

## 재사용과 추가

- 기존 OperationsController/Service/Repository, 실제 DB ADMIN/credential 검증, /admin의 권한 게이트·목록 디자인, 기존 미션 evidence·receipt·콘텐츠 revision/승인 pointer·댓글/하트·공식 게임 slot을 재사용했다.
- 순수 AwardCalculator/AwardModels만 분리하고 기존 운영 서비스/저장소에 읽기 경로를 추가했다. 새 테이블/마이그레이션/외부 의존성/완료 보상 쓰기는 없다.
- 관리자 탭 **시상 후보**에서 네 부문의 잠정 순위, 원본 근거 종류/ID/시각, 최초 프로필 등록, 동점·공동 순위·복수 부문 1위, 미처리/무효 근거 및 게임 검토 목록을 확인한다. 결과는 페이지당 20개, 서버 최대 50개로 제한한다.
- 전체 계산은 현재 관리자 권한 확인부터 한 REPEATABLE_READ 트랜잭션에서 실행한다. no-store를 유지하고 원문/실명/MM 식별자/비밀값·설문답·게임 payload를 응답하지 않는다.
- 후보가 0명이면 빈 상태, 활동 점수가 0이면 실제 0, 완전 동점이면 공동 순위로 표시한다. 동점 처리를 위해 user_id/승인 순서/임의 난수를 수상 규칙으로 사용하지 않는다.
- 1인 1수상과 다섯 유효 미션 완료자 조건은 확정 기록을 근거로 적용하되, 복수 부문 배정·차순위 승계는 미확정이므로 표시만 한다. 랜덤 추첨·최종 확정/정정·발표·경품/DM은 만들지 않았다.
- 프론트·SSR·도구 54 PASS, 실제 Java 순수 계산 harness PASS. Spring/JDK21/MySQL/Redis/실렌더는 미검증이며 로컬 후속 실행이 필수다.

## 과거 시상 작업 조사와 한계

이번 GitHub GET에서도 feat/network-graph HEAD=86ad1dd87a64a1879766dc7682ff8eafc676e68d를 확인했다. 기존 통합 조사에서 f77aba1c3e9944482fb805269d8a67c988456662는 86ad1dd의 조상이고 당시 tree/운영 계약/두 커밋 사이 변경에는 시상 후보 구현이 없음을 확인했다. 이번 최신 미션 패치 기준의 Operations 소스에도 후보 구현이 없어 추가했다.

미제공 TAGME_awards_candidates_f77aba1.zip 안의 내용은 확인/재사용하지 않았다. 해당 ZIP에 구현이 없다고 단정하지 않는다. 나중에 확보되면 이 구현과 중복 개발한 채 합치지 말고 계산/운영 정책/원본 검증 테스트를 비교해야 한다. f77aba1 자체로 checkout/merge하거나 현재 UI·SHOW ME 자동 정책을 내리지 않는다.

근거: https://github.com/anonymity-developer/connection_16/compare/f77aba1...86ad1dd 및 기존 docs/final/SUMMARY.md. 시상 규칙과 미확정 사항은 [RULES.md](RULES.md), 운영 절차는 [REVIEW.md](REVIEW.md), 실제 실행과 한계는 [TEST_RESULTS.md](TEST_RESULTS.md).

기존 인증·네 NFC·SHOW ME 자동 지급·SAY 파이팅!·검수/하트·그래프 코드는 보존했다. 실제 DB 조회/쓰기·수상 확정·MM 요청·commit/push/PR/merge/배포는 하지 않았다.
