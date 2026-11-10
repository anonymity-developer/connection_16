# 행사 직전 리허설 결과 — 2026-11-08 KST

## 실제 실행

| 검사 | 결과 | 근거 |
| --- | --- | --- |
| 최신 선행 패치 재구성 | PASS | v5 후속 → final 통합 → 미션 → 시상 순서로 격리 worktree 적용. 시상 ZIP changed-files 바이트 대조 |
| FAST TAP 취소 경합 재현 | FAIL (수정 전) → PASS (수정 후) | 기존 취소 응답 projection을 그대로 분리한 두 테스트가 실패. 수정 후 같은 테스트 두 개 통과. 정상 중단/VALID/검토 대기/만료 보존 테스트도 추가 |
| frontend lint | PASS | npm --prefix frontend run lint |
| frontend TypeScript/Vite | PASS | npm --prefix frontend run build |
| 프론트/SSR/도구 단위 | PASS 60/60 | npm --prefix frontend run test:unit. 기존 54개 재사용 + 취소 3개/리허설 안전 계획 3개 |
| 실행 계획 | PASS | node scripts/rehearsal/plan.mjs. 기본값에는 DB/브라우저/장애 행동이 없고 미실행 항목을 UNVERIFIED로 표시 |
| Java21/Spring/bootJar | 미검증 | Work에 Java21 없음. 이전 Java17 순수 시상 계산 PASS는 이전 산출물 결과이며 이번 Spring 검증으로 승계하지 않음 |
| 새 HTTP+DB 통합 및 기존 DB 회귀 | 미검증 | Docker 없음. 실제 MySQL/Redis 대체 DB를 만들거나 운영 서버에 접속하지 않음 |
| Redis/MySQL pause/복구 | 미검증 | 전용 Testcontainers ID에만 동작하는 opt-in 테스트 작성. 실제 실행하지 않음 |
| 브라우저 실렌더 | 미검증 | 기존 Playwright 계약 재사용·취소 경합 추가, 결과/콘텐츠 폭을 360/375/390/412/430/768/1440으로 확대. Work 실렌더 실행 안 함 |
| PowerShell·iOS/Android·실물 NFC·앱 재시작 | 미검증 | 실제 Windows/기기/격리 운영 환경 필요 |

패치 적용·역적용 결과는 ZIP 루트 PATCH_VERIFICATION.md. 실제 테스트 출력은 ZIP logs/ 참조. 수정 전의 의도한 회귀 FAIL을 현재 남은 제품 FAIL로 해석하지 않는다.

## 확인된 결함 REH-01

**P2 / 공식 기회 UI와 서버 slot 불일치.** A탭이 준비 도전을 취소 → 서버는 취소 반영 → B탭이 새 도전 시작 → A탭에 과거 CANCELLED 응답 도착. 기존 cancel()은 그 응답만 보고 officialAvailable=true/attempt=null을 표시했다. 서버가 추가 공식 기회를 허용한다는 증거가 아니며 서버 보상/1회 제한 우회가 재현된 것은 아니다.

취소 응답 처리를 cancelTapAttempt로 분리하고 기존 recoverTapStart를 재사용해 CANCELLED인 경우 실제 slot을 GET 재조회한다. 실패하면 사용 가능 상태를 만들어내지 않으며, 새 도전이 있으면 그 상태를 사용한다. 소모된 도전(INTERRUPTED/VALID/REVIEW_REQUIRED/EXPIRED)은 기존대로 유지한다. 시작 key는 실제 사용 가능 상태가 확인될 때만 초기화한다. 서버·NFC·공식 1회 정책은 변경하지 않았다.

## 케이스 / 실행 범위

| 구간 | 추가·재사용한 검사 | 이번 실행 |
| --- | --- | --- |
| 익명 태그 → OTP 가입 → 프로필 | 새 EventRehearsalIntegrationTest. 실제 CSRF/쿠키/세션 회전·동의 누락·프로필 저장·NFC 없이 첫 S·저장 응답 유실 후 stale 재전송·재로그인/다른 탭 | 미검증 |
| 행사 전 → OPEN | 새 HTTP 사전 등록/로그인/재확인 + 기존 ParticipationIntegrationTest 동시 재확인/순번 | 미검증 |
| 네 NFC | 새 HTTP 잘못된/없는 토큰, 반복 resolve, 다른 미션 수행 우회 차단. 기존 auth-nfc 브라우저 로그인 복귀·만료 | 미검증 |
| SYNC | 새 실제 fixture JSON 등록·빈 응답 거절·최초 결과/반복·추천 빈 목록/설문 후 추천·그래프 기존 간선 불변. 기존 Results/Discovery 통합과 결과 CTA SSR | API/DB 미검증, 기존 관련 순수 단위 포함 60 PASS |
| 소원 | 새 서로 다른 참가자·같은 계정 동시 동일 key 제출/승인/수정/재승인. 기존 Content 통합·소원 성공/실패 연출 fixture/운 점수 | API/실렌더 미검증, 순수 단위 PASS |
| FAST TAP | 새 실제 서버 5초 준비/10초 도전 시간 경과 후 제출·재전송/공식 기회 재사용 거절. 기존 Results 통합 중단·이상 입력·rollback·다른 사용자 소유권. 취소 경합 순수 회귀/브라우저 fixture | 새 취소 단위 PASS, API/브라우저 미검증 |
| 응원·반응 | 새 독립 카드/승인/Y, 하트 반복·취소·재등록, 소원 댓글 승인. 기존 익명/닉네임·숨김/재검수/댓글·하트 테스트 | API/브라우저 미검증 |
| 최종 완료·시상 | 새 마지막 완료 DB 장애 주입 후 전체 evidence/완성 순번 rollback→동일 승인 재시도, 5글자·2명 후보·일반 계정 403. 기존 Operations 승인 지연/동시 snapshot/동점/무효 근거 | 미검증 |
| 의존성 장애 | 신규 opt-in pause/unpause. 새 테스트만 1초 Redis/1.5초 JDBC timeout·15초 복구 대기. 기존 Infrastructure health | 미검증 |

새 API 통합 테스트는 새 테스트 계정과 TEST ONLY 정책/문항을 임시 컨테이너에만 생성한다. 실제 MM client/provider는 Mockito로 대체한다. 실제 10초 도전은 브라우저 터치가 아니라 HTTP 입력 fixture이며 실물 게임 경험을 검증했다고 주장하지 않는다. 기존 운영 일정·동의문을 확정/변경하지 않는다.
