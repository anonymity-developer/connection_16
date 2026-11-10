# 실행·미검증 결과 — 2026-11-08 KST

| 항목 | 상태 | 근거 |
| --- | --- | --- |
| 기존 통합 ZIP 기준 재현 | PASS | 첨부 primary 적용 및 changed-files 전체 바이트 대조 |
| ESLint | PASS | npm --prefix frontend run lint |
| TypeScript/Vite | PASS | npm --prefix frontend run build |
| 단위/SSR/도구 | PASS 50/50 | npm --prefix frontend run test:unit. 기존 테스트 재사용 + 신규 8개, NFC 생성 기대값4/SHOW 안내 기대값 갱신 |
| Java 단위·컴파일 | 미검증 | JDK17만 존재. ShowMeAutomaticCompletionTest 추가, ShowMeCompletionRuleTest 기대값 갱신. JDK21로 실행 필요 |
| MySQL/Redis 통합 | 미검증 | Docker 없음. ParticipationIntegrationTest의 자동 지급/사전 등록/동시 재확인/CSRF/중복·기존 QA 회귀를 갱신 |
| 실제 브라우저 | 미검증 | fixture 스크립트에 새 POST, 네 NFC 복귀, 소원 실패/접수/새로고침/reduced-motion 검사 반영. Work 실렌더 PASS 아님 |
| iOS/Android/NFC/PowerShell | 미검증 | 실제 기기·Windows 실행 필요 |
| MM/운영 DB | 미실행 | 실제 접속/DM/쓰기 없음 |

새 단위: OPEN 재확인/행사 전후·stale 차단/실패·조건 미충족/경합에서 false 결과 재조회/추천 CTA/소원 revision 중복 억제·운 점수 독립/텍스트 검수 상태/SAY 이름·식별자 유지.

테스트 실행 중 Vite SSR 개발 WebSocket 포트 경고가 있었으나 50개는 exit 0으로 완료했다. 이를 브라우저 성공 또는 서버 컴파일 성공으로 해석하지 않는다. 프론트 브라우저 fixture의 실제 실행과 Java Testcontainers의 실제 성공 여부는 Windows에서 따로 기록해야 한다.

패치의 적용/역적용·바이트 일치 결과는 ZIP 루트 PATCH_VERIFICATION.md에 별도 기록한다. 기존 통합본의 42 PASS는 과거 결과이며, 이번 50 PASS는 후속 수정 상태에서 새로 실행했다.
