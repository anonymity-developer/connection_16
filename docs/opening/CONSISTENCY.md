# 패치·기능 정합성 점검

기준 GitHub feat/network-graph HEAD는 이번 조회에서 86ad1dd87a64a1879766dc7682ff8eafc676e68d. 사용자 로컬 적용 상태를 가정하지 않고 별도 work/opening-check에 선행 UI v5→후속→final→missions→awards→rehearsal→post-event를 순차 적용했다. 파일 겹침과 적용/역적용은 ZIP PATCH_VERIFICATION.md 및 PATCH_OVERLAPS.md에 기록한다. 여러 패치가 같은 파일을 바꿔도 의도된 순차 수정이면 충돌로 단정하지 않는다.

| 항목 | 코드 근거·판정 | 검증 한계 |
| --- | --- | --- |
| 사용자 노출 미션명 | frontend/src/features/missions/model.ts의 SAY 파이팅!, 내부 SAY_SOMETHING 유지. frontend/src의 과거 영문 표시명/SHOW ME 태그 안내 잔존 검색 없음 | 브라우저/실제 DB 렌더 미검증. 옛 DB mission title은 역사 자료이며 프론트 공식 model을 사용 |
| SHOW ME | ParticipationService/MissionCompletionService 및 show-me/reconcile, profile 저장의 서버 조건·OPEN 실제 지급. 사전 준비는 저장과 지급 구분 | 기존 Java 회귀·리허설 코드 재사용, 이번 Spring 미실행 |
| 4개 NFC | create-tag-bundle.mjs 대상4개, ParticipationService 토큰 검증/미션별 해금. pending/resume 로그인 복귀 | 기존 프론트 계약 포함 단위 실행; 실제 토큰/실물/QR·실서버 미검증 |
| 시상 | AwardCalculator 변경 없음. OperationsRepository는 기존 고정 관찰 시각 경로. 전체완성/댓글/하트/공식 VALID 근거 검증 유지 | Spring·MySQL snapshot/관리자 권한·정산 회귀 실행 미검증 |
| 관리자 결과 API | ResultsPanel/results-api ↔ PostEventController /api/v1/admin/results, export, 제한 participants 경로. 기존 탭 유지 | SSR/단위 및 API클라이언트 거절 회귀 실행; 실제 서버권한 미검증 |
| 사후 분석 | DATA_DICTIONARY와 activity-extract.sql에서 SHOW NFC 제외, UTC actual timestamps, HEART_CURRENT/최초방문, TAP_SCHEDULED 의미 구분. 반출 비활성·퍼널 미구현 유지 | Python 계약 실행, SQL/실제 데이터 미검증 |
| 실행 경로 | rehearsal/plan.mjs의 기존 npm suite·browser script·Java class 파일 존재 검사. readiness의 새 선택형 공개 GET은 loopback만 | 실제 PowerShell/Java/Docker/브라우저 미실행 |
| 동의 최신버전 | ParticipationRepository.policyState/agreed 및 ParticipationIntegrationTest.policyReplacementAndWithdrawalRequireFreshConsentButPreserveHistoricalCompletion | 서버 코드 근거, Java 실행 미검증. 가입 전 안내 누락은 해결됐다고 주장하지 않음 |
| MM·검수 | AuthProperties의 배타적 설정/한도/HMAC, MattermostClient finally logout·자기DM 차단, HttpModerationProvider REVIEW_REQUIRED 및 기존 운영 검수 | 실제 승인/외부권한/DM 미검증. 자동 제공자 미설정은 수동 인력 승인 필요 |

## 확인·수정한 결함

1. **P1 진단 오탐:** audit.mjs가 gameDuration>0이면 통과하여 9초 설정에도 readyForMissionSmoke=true. ResultsService는 gameDuration!=10이면 GAME_CONFIG_INVALID. 원본/수정 후 같은 가상 설정을 실행해 true→false 재현. 진단만 gameDuration=10으로 맞췄다.
2. **P1 진단 날짜 오탐:** Date.parse가 2030-02-30을 3/2로 정규화하여 통과. 원본/수정 후 true→false 재현. 엄격한 달력 검사·UTC DATETIME(6) 비교로 수정. 일정·서버 정책은 변경 없음.
3. **P2 발급 도구 안내:** 실제 4개 태그를 만들면서 성공 문구만 five라고 표시. four로 수정. Work는 발급 CLI를 실행하지 않았고 기존 테스트의 격리 가상 토큰만 사용.
4. **안전한 진단 출력:** 기존 공개 probe가 임의 body.status 문자열을 출력할 수 있어 허용 enum만 반환하도록 축소, 익명 GET/캐시금지 명시. 테스트 응답에 임의 문자열을 넣어 비노출 확인. 실제 비밀정보 유출 사례를 발견했다고 주장하지 않음.

기존 공백 동의 본문 판정/가입 전 안내·MM본인DM 제한은 이전에 확인된 운영 위험으로 유지하며 이번에 정책·인증을 임의 변경하지 않았다. 신규 서버 API/DB migration/UI 리디자인 없음.
