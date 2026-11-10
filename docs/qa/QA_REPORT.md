# TAG ME 출시 전 QA — 2026-11-08 KST

> 후속 미션 기획 변경은 [최신 적용 가이드](../missions-final/APPLY.md)와 [구현 요약](../missions-final/SUMMARY.md)을 우선한다. 이 문서의 실행 결과는 당시 이력이다.

> 최초 QA 단계의 실행 이력이다. 최종 통합·UI 보완의 새 실행 결과와 시상 저장소 조사 범위는 [최종 통합 검증](../final/VERIFICATION.md)과 [통합 요약](../final/SUMMARY.md)에 별도로 기록한다.

기준: `86ad1dd`, 기존 UI 1~5차는 보존. `TAGME_ui_v5_followup_86ad1dd.zip`과 `TAGME_operational_readiness_86ad1dd.zip`의 실제 파일을 읽고 패치를 검토했다. 두 ZIP을 사용자 로컬에 적용됐다고 가정하지 않았다. 원본/기존 작업은 별도 worktree로 보존. 실제 계정·DB·DM에는 접속하지 않았다.

## 결론

출시 준비 완료로 판정하지 않는다. 실행 가능한 프론트/순수 상태 테스트는 통과했으나 서버의 실제 MySQL·Redis·동시성 통합 및 브라우저/실물 NFC는 미검증이다. 기준 커밋에는 시상 후보 집계·최종 확정·지급 기능이 없다. 별도 f77aba1 기반 시상 ZIP은 이번 입력 두 산출물이나 기준 커밋에 포함되지 않으므로 검증 대상으로 추정해 넣지 않았다.

## 실제 결함·재현·최소 수정

| ID / 심각도 | 재현·원인 | 수정·검증 |
| --- | --- | --- |
| QA-01 / P1 모바일 참가 흐름 | FAST TAP 시작 요청 응답 유실 → 다른 탭에서 준비 중 취소 → 같은 키로 재시도. 서버는 그 키의 CANCELLED 원본을 반환하지만 TapMission.start는 무조건 officialAvailable=false를 설정. 실제 슬롯이 비어도 시작 버튼이 사라짐 | CANCELLED 응답에 한해 GET /tap/me로 현재 슬롯 재확인, 성공 후 이전 시작 키 해제. 다른 탭의 새 도전이 있으면 그 상태를 그대로 표시. 실패 시 획득/재도전 가능 상태를 만들지 않음. 기존 상태 처리를 추출한 순수 재현에서 수정 전 3 FAIL/1 PASS → 수정 후 4 PASS. 실제 React+브라우저 클릭 재현은 미실행이며 기존 check-results-ui에 회귀 시나리오 추가 |
| QA-02 / P2 최종 지급 검증 누락 | eligible=true인 공개 프로필이지만 소개/관심사 없음. SHOW_ME의 유효 PROFILE/TAG_UNLOCK receipt로 공통 completion을 직접 호출하면 ShowMeCompletionRule이 일반 eligible만 확인하여 true 가능 | 최종 rule을 기존 showMeEligible로 변경. 현재 save/resolve에는 이미 선행 검사가 있어 공개 HTTP API 우회가 실증됐다는 의미는 아님. 기존 정책을 최종 방어선에도 적용. JUnit 2개와 Testcontainers 1개 추가. JDK 21/Docker 부재로 실행/컴파일 미검증; 수정 전후 서버 PASS라고 주장하지 않음 |
| QA-03 / P2 이전 점검 도구 오분류 | readiness.audit는 contentSettings=0이면 전체 준비 실패. 실제 ContentService와 docs/content.md는 운 점수를 선택 설정으로 명시, null 상태 참여를 허용 | 선택 운 설정이 없는 경우 정상 허용하도록 별도 qa-over-readiness.patch 제공. 도구 8개 PASS. 실제 운 점수/경품 정책은 정하지 않음. 기존 readiness 문서의 ‘필수’ 표현은 이 QA 보고서의 선택 설정 설명으로 정정 |

서버 인증·CSRF·실제 보상 규칙·DB 스키마·운영 권한·공식 도전 횟수는 완화하지 않았다. UI 디자인·미션 카피·에셋은 변경하지 않았다. QA-01의 상태 복구 안내 문구만 추가했다.

## 출시 요구 대비 결과표

PASS는 이번에 실행한 검사만 뜻한다. 아래 ‘미검증’은 코드 검토와 테스트 존재 확인은 했지만 서버/브라우저에서 성공을 실행 확인하지 못했다는 의미다.

| 영역 | 상태 | 근거/정상·오류·경계 |
| --- | --- | --- |
| 프론트 인증 입력·CSRF 클라이언트·세션 만료 이벤트 | PASS (단위 범위) | 기존 auth-validation + 신규 api-client-security: 비밀번호 URL/CSRF GET 미포함, 로그인 후 CSRF 갱신, 401 재인증 알림, 네트워크 실패 쓰기 자동 재전송 없음 |
| OTP→가입→로그인, 만료·재사용·resend·비밀번호 버전 | 미검증 (서버) | AuthIntegrationTest: 선행0, 목적/세션 바인딩, 원자 소비, 재발송 이전 proof 무효, 동시 비밀번호 작업. MM SenderSessionTest: 오류·429·timeout·logout. 실제 외부 요청 없음 |
| 행사 전/OPEN/마감, 정책·프로필 변경 | 미검증 (서버) | requireOpen(true)의 BEFORE_OPEN 사전 프로필 옵션과 requireOpen(false) 미션 차이, EVENT_NOT_CONFIGURED/POLICIES_NOT_CONFIGURED/POLICY_CHANGED/PROFILE_CHANGED. 기존 ParticipationIntegrationTest 재사용 |
| NFC 최초/재스캔/로그인 복귀/만료 | 미검증 (서버·기기), PASS (기존 경로 순수 단위) | 43자리 정규 토큰 및 해시, 서버 pending 900초, session rotation 보존, 태그 조회/해금은 resolve에서만. 기존 auth-nfc-ui는 브라우저 미실행 |
| SHOW ME | 미검증 (서버) | 소개·관심사·프로필·현재 동의·태그·행사/활성 조건. QA-02 보강 및 단위/통합 회귀 추가 |
| SYNC ME | 미검증 (서버) | ResultsIntegrationTest: 동일 버전/답변 canonical replay, 다른 답변 거절, 동시 최초 결과 하나. 현재 설문 변경은 기존 결과를 재계산하지 않음 |
| MAKE A WISH | 미검증 (서버) | ContentIntegrationTest: 슬롯 하나, revision/receipt, 첫 승인 후 A, 수정 반려 시 승인본/최초 evidence 유지. 운 설정은 선택이며 기존 슬롯 소급 재추첨 없음 |
| FAST TAP | PASS (재시도 상태 순수 단위), 미검증 (서버/브라우저) | QA-01 수정. ResultsIntegrationTest: 같은 키 동시 시작, 준비 취소, 10초·제출 마감, 중단/검토 상태 미지급, 동일 finish payload 중복 방지 |
| SAY SOMETHING/댓글/하트 | 미검증 (서버) | Content/ReactionIntegrationTest: 승인 전 비공개, 익명 링크, idempotency key 충돌, 명시적 desired like 상태·복합 PK, 본인 하트 차단, 철회 상태 공개 제외 |
| 수집판/완료/동료 공개 범위 | 미검증 (서버), PASS (기존 프론트 표현 단위가 포함된 통합 패치 구성) | 공통 완료 user PK/evidence unique/전역 sequence lock. Discovery는 조회마다 공개 동의 필터, 본인 직접 간선, 익명 like 제외. source 검토는 런타임 보장이 아님 |
| 운영자 검수·감사·권한 | 미검증 (서버) | SecurityConfig ROLE_ADMIN + 서비스 DB 역할 재확인, job fence/content version, 감사 실패 트랜잭션 롤백. OperationsIntegrationTest 재사용 |
| 시상 후보/확정/지급 | FAIL (출시 요구 미구현, 실행 테스트 실패와 구분) | 기준 소스에 award aggregate/controller/migration/API 없음. 완료 순번/점수 데이터가 존재한다고 시상 기능 구현으로 판단하지 않음 |
| 360/390/430px·Safari/Android·키보드/모달 | 미검증 | 브라우저/실기기 실행 불가. 기존 7개 fixture 계약 스크립트 보존, results 계약에 재현 추가. lint/build는 실렌더 PASS가 아님 |

## 동시성·재시도: 정적 근거와 기존 통합 검사

모든 아래 DB 시나리오는 **미검증(서버 실행 필요)**. 버튼 disabled만을 근거로 PASS 처리하지 않는다.

| 상황 | 서버 방어/기존 시험 |
| --- | --- |
| 같은 NFC 20회/여러 탭 | 사용자 행 잠금 + progress/receipt 고유성. twentyConcurrentUnlocksHaveOneReceiptAndOneCompletion |
| 미션 완료/최종 두 미션 동시 완료 | completion 사용자 잠금, evidence unique, event_completions 사용자 PK/순번 UNIQUE, 전역 sequence FOR UPDATE. differentFinalMissionsAllocateOneOrderAndRetriesKeepIt |
| 여러 참가자 최종 완료 | 각 사용자 먼저 잠금 후 글로벌 sequence만 직렬화. finalCompletionForDifferentUsersSerializesOnlyTheGlobalSequence |
| 승인과 수정/워커 지연 응답 | 사용자·콘텐츠 잠금, pending revision 비교, fence·lease, stale 응답 무시. staleAndDuplicateWorkerResponsesCannotOverwriteTheNewRevisionOrGrantTwice |
| 중복 소원/응원/댓글 | payload hash+idempotency key, 사용자 잠금, 최초 slot UNIQUE. 다른 본문으로 같은 키는 409 |
| 중복 하트/취소/네트워크 재시도 | toggle이 아니라 desired boolean + (card,user) PK, like UPSERT/unlike DELETE, 사용자/작성자 순서 잠금 |
| 마감 직전 | 서버 UTC/received_at/해금 시각 및 moderation 마감 검증. 락 대기 후 현재 행사 시간도 확인하므로 ‘마감 전 버튼을 눌렀다’만으로 성공 보장하지 않음. 규칙을 임의 변경하지 않음 |
| OTP 중복/만료/재사용 | Redis proof HMAC·TTL·Lua 원자 소비, resend/rate key, credential version 잠금. 실패 시 계정/receipt 롤백과 OTP 재사용 불가 시험 |
| admin 중복/경쟁/재시도 | expectedFence/version로 409, job fence 증가, 감사와 판정 원자 저장. 실패 후 최신 서버 상태 조회가 우선 |

테스트 안의 DELETE/FLUSH는 Testcontainers 전용 DB에서만 수행해야 한다. 기존 Compose/실제 참가자 DB에 복사해 실행하지 않는다. 고동시성 통합 테스트가 있다는 사실은 실제 행사 트래픽 처리량 검증이나 부하 수치가 아니다.

## 시상 코드 점검

- event_completions.completion_order는 **운영상 처리 완료 순번**, API는 awardConfirmed=false. ‘최초 완성상 순위’로 쓰면 안 됨.
- mission_completion_evidence.accepted_received_at은 receipt/해금/추가 조건 시각의 최댓값을 보관한다. 검수 지연 보정·동점 처리·중복 수상·반려/철회 후 후보 제외 정책은 이 커밋에 시상 알고리즘으로 구현돼 있지 않음.
- 댓글왕의 ‘서로 다른 타인·승인 댓글’ 집계 쿼리도 없음. wish_comments가 존재한다고 단순 COUNT(*)를 시상에 쓰지 않음.
- 운 점수/FAST TAP 기록에는 null/REVIEW_REQUIRED/INTERRUPTED 등이 존재할 수 있다. 콘텐츠 검수 UI를 게임 검토·시상 확정 화면이라고 간주하지 않음.
- 반복 집계 snapshot·취소·확정·지급 idempotency/감사는 미구현이므로 통과 판정 불가. 규칙을 확정하거나 새 시상 기능을 이번 버그 수정에 끼워 넣지 않음.
- 별도 시상 산출물을 통합한다면 그 정확한 기준 코드와 현재 패치 조합으로 후보/동점/변경/지급 테스트를 별도 실행해야 함.

## 현재 실행 결과 및 한계

- 원본+QA: frontend lint/build, 단위 21개 PASS (기존 13 + 신규 8). QA-01의 기존 동작 추출 재현 3 FAIL/1 PASS 후 수정 4 PASS.
- 두 기존 ZIP+QA 조합 lint/build·단위 및 optional overlay, 적용/역적용 결과는 ZIP TEST_RESULTS.md에 실제 실행 후 기록.
- JDK는 17만 있고 Docker 없음. 이전 동일 세션의 Gradle 다운로드 차단을 확인했으므로 같은 다운로드를 반복하지 않음. Java JUnit/bootJar/실 MySQL·Redis 통합은 **미실행/컴파일 미검증**. H2나 JDK17로 대체하지 않음.
- 실제 MM 로그인·OTP·계정/정책/DB 쓰기·지급·운영 데이터 변경 없음. commit/push/PR/merge/배포 없음.
