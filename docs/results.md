# SYNC ME와 FAST TAP 구현

성향 결과와 공식 탭 도전을 서버에 연결한다. 현재 공개 순위·기록 갱신 소감은 [확정 미션 규칙](mission-rules.md), 네 게임 초기화는 [소프트리셋 계약](direct-publication-and-reset.md)을 따른다. 실제 참가자 결과·일정·태그나 외부 발송은 만들지 않는다.

## 공통 조건과 저장

V1~V4는 수정하지 않고 V5를 추가했다. 새 테이블은 sync_questionnaires/current_sync_questionnaire/sync_results/tap_slots/tap_attempts/tap_attempt_events다. 모든 변경은 로그인과 CSRF를 요구한다. 새 수행에는 ACTIVE와 credential version, 행사 진행, 미션 활성/태그 해금, 필수 프로필과 두 현재 동의/공개 상태가 필요하다. 동일 사용자 행부터 잠가 다른 탭/동시 요청을 직렬화한다.

결과/도전/이벤트/receipt/글자는 같은 트랜잭션에 저장한다. SYNC_RESULT의 source_id는 사용자 ID, GAME_ATTEMPT의 source_id는 attempt ID다. 각각 실제 결과 존재와 VALID 상태를 확인하는 CompletionRule을 등록했다. SHOW_ME와 소원/응원까지 다섯 미션이 공통 완료 경로를 사용한다. 운영상 전체 완료 순번과 시상 순위는 계속 구분한다.

## SYNC ME

V18은 사용자 승인 문항 `sync-2026-11-13-v1`을 등록하고 현재 포인터를 이 버전으로 바꾼다. 설정이 없으면 questions.available=false, 빈 문항/NULL 버전을 반환하고 제출은 503 QUESTIONNAIRE_NOT_READY로 거절한다. 잘못된 설정도 가짜 결과로 처리하지 않는다.

문항 버전은 sync_questionnaires.version에 고정하고 current_sync_questionnaire.id=1이 현재 버전을 가리킨다. 운영자는 기존 버전 JSON을 수정하지 않고 새 버전과 현재 포인터를 별도로 관리한다. 결과는 당시 버전, 정규화된 답변, 축 점수, type_code, payload_hash, 최초 서버 접수 시각을 보관한다. 현재 버전을 바꿔도 기존 결과를 재계산하지 않는다.

### 설정 구조

| 항목 | 검증 |
| --- | --- |
| title | 공백이 아닌 최대 100 UTF-16 단위 |
| axes | 1~4개. 고유 id, 서로 다른 대문자 한 글자의 negative/positive, tie=NEGATIVE 또는 POSITIVE |
| questions | 1~20개. 고유 id와 text, 2~6개 옵션 |
| options | 질문 안의 고유 id, label, 모든 축에 대한 -5~5 정수 점수 |
| types | 모든 축의 positive/negative 조합을 정확히 한 번씩 정의. code/title/description |

id는 `[A-Za-z0-9_-]{1,32}`다. 사용자 제출은 questionnaireVersion과 questionId/optionId만 받는다. 축 점수나 유형 코드는 받지 않는다. 누락/중복/알 수 없는 질문·선택지는 400이며 서버가 문항 순서대로 답변을 정규화한다. 점수를 합산하고 0점은 해당 축의 tie 방향으로 정한다. 결과는 재미용 대화 소재이며 전문적인 성격 진단으로 표시하지 않는다.

같은 버전/답변 재시도는 최초 결과를 반환하고 다른 답변/새 버전으로 결과를 바꾸는 요청은 409다. 같은 답변의 동시 제출도 결과/receipt/evidence를 하나만 만든다. 이전 결과 재조회와 같은 제출 재시도는 행사 종료/공개 철회 후에도 본인에게 허용하지만 신규 수행은 차단한다.

### 승인 문항과 유형

[sync-questionnaire.proposal.json](sync-questionnaire.proposal.json)은 사용자 승인본과 동일한 세 축·아홉 문항·여덟 유형을 담는다. 축은 사교 방식(공감/표현), 협업 방식(조율/추진), 행동 방식(신중/도전)이며 축마다 세 문항이 있어 점수 동점은 발생하지 않는다. Java 서버의 실제 파서와 점수 계산으로 여덟 결과가 모두 도달 가능한지 자동 검증한다.

V19는 이 승인본을 새 불변 버전으로 추가하고 현재 포인터만 갱신한다. 기존 질문 버전과 결과는 삭제하거나 재계산하지 않는다. 프론트엔드는 문항이나 점수표를 하드코딩하지 않고 서버가 반환한 질문과 결과만 표시한다. 참가자 화면은 한 번에 한 문항만 보여주고, 현재 문항과 진행률을 표시하며 이전에 고른 답은 제출 전까지 유지·수정한다. 마지막 문항에서만 결과 제출을 제공한다.

결과 화면은 같은 `questionnaire_version`과 `type_code`를 가진 현재 공개 가능 동료를 닉네임 목록으로 제공한다. 조회 자체는 방문을 만들지 않고, 닉네임을 눌러 기존 프로필 열기 POST에 성공할 때만 방문 관계를 기록한다. 관리자 미리보기는 실제 동료 목록을 조회하지 않는다.

## FAST TAP 공식 도전

연습과 공식 도전은 분리한다. 연습은 브라우저의 시간/입력만 사용하며 API 시작/종료, 글자, 공식 점수 또는 참여권을 만들지 않는다. 공식 도전은 사용자당 슬롯 하나와 변경하지 않는 도전 이력을 둔다. 슬롯의 복합 FK는 다른 사용자의 attempt를 가리키지 못한다.

### 시작과 취소

POST /tap/start는 16~64자 `[A-Za-z0-9_-]` Idempotency-Key를 요구한다. 같은 사용자/key는 같은 attempt를 반환한다. 다른 key로 현재 공식 기회를 우회하지 못한다. 준비 중 취소된 key를 재사용하면 그 취소 이력을 반환하며 새 도전에는 새 key를 쓴다.

서버 MySQL UTC 시각으로 startsAt=현재+5초, endsAt=startsAt+10초, submitDeadline=endsAt+5초를 발급한다. event_settings.game_duration은 이번 공식 규칙에서 정확히 10이어야 하며 다르면 503으로 막는다. 제출 유예까지 행사 신규 제출 마감 안에 들어와야 새로 시작한다.

준비 시작 후 startsAt **이전** 취소만 CANCELLED로 남기고 슬롯을 복구한다. 시작 이후 취소/이탈은 INTERRUPTED, 제출 기한 후 중단 확인은 EXPIRED이며 기회를 복구하지 않는다. 이전 시도를 삭제하거나 참가자가 임의로 새 공식 도전을 만들 수 없다. 관리자 승인 재도전은 아직 미구현이다.

GET /tap/me는 현재 시각으로 COUNTDOWN/PLAYING/AWAITING_SUBMISSION/EXPIRED를 계산한다. 조회만으로 쓰지 않는다. 네트워크가 끊겨도 기존 슬롯이 남아 시간 경과 후 자동으로 새 기회가 생기지 않는다.

### 제출과 검증

finish는 attemptId, taps(상대 밀리초 배열), interruptions(type/atMs)을 받는다. 클라이언트 tapCount/점수/상태/사용자 ID는 거절한다.

| 확인 | 처리 |
| --- | --- |
| taps 최대 1000개, 각 0~9999ms, 오름차순 | 형식/순서 위반은 400. 서버가 배열 크기에서 count 계산 |
| interruption 최대 20개, HIDDEN/PAGE_HIDE, -5000~15000ms | 존재하면 INTERRUPTED, 글자 미지급 |
| 서버 접수 시각 < endsAt | 409 TAP_TOO_EARLY |
| 서버 접수 시각 > submitDeadline | EXPIRED, 글자 미지급 |
| 한 번이라도 1ms 미만 간격 (정수 ms 기록에서 같은 시각의 입력) | INVALID / DUPLICATE_MILLISECOND 자동 판정, 글자 미지급 |
| 그 밖의 허용된 기록 | VALID 잠정 점수, 공통 완료 호출 |

입력 간격은 1ms 이상을 허용하며 200회 초과 조건은 없다. 현재는 관리자 검토 없이 서버가 정상/무효를 바로 판정한다. 기존 REVIEW_REQUIRED 기록은 원본 이벤트·해시·횟수·기한으로 자동 재판정하며, 자격이 충족된 경우만 원래 접수 근거로 공통 완료를 호출한다. [현재 정책과 소프트리셋](direct-publication-and-reset.md).

의심 기준은 검토 분류이며 부정행위 확정 판정이 아니다. 0회도 10초 도전을 끝낸 유효 점수로 저장할 수 있다. 웹 이벤트를 조작하거나 자동 생성하는 행위를 완전히 막는다고 주장하지 않는다. 시상 확정은 별도이며 잠정 기록을 수상으로 표시하지 않는다.

접수 시각과 처리 시각을 구분한다. 종료/유예 검증은 서버 접수 시각으로 하며, 진행 중인 행사/설정/계정 상태는 실제 쓰기에서 재확인한다. 이벤트와 상태/receipt/글자는 같은 트랜잭션이므로 실패 시 전부 롤백한다.

성공 또는 검토/만료 처리 뒤 동일 이벤트 payload 재시도는 최초 상태를 반환한다. 바뀐 이벤트 payload는 409 TAP_PAYLOAD_CHANGED다. 해시는 필드 순서가 고정된 서버 DTO로 계산하며 프로세스를 재시작해도 같은 요청이다. 이미 중단/취소된 도전에는 새로운 finish를 붙이지 못한다.

### 브라우저 흐름과 장애 복구

서버 시각과 요청 왕복 시간의 절반을 기준으로 performance.now() 타이머를 맞춘다. 이 보정은 연출용이며 공식 기한 판단을 대신하지 않는다. 탭 영역만 touch-action:none/user-select:none을 사용하고 pointerdown 및 Space/Enter를 처리한다. 키보드 자동 반복과 pointer/click 중복 집계는 하지 않는다.

준비/플레이 중 visibilitychange/pagehide/내부 이동은 중단 처리를 요청한다. keepalive 요청도 전달 보장이 아니므로 서버 슬롯/기한이 최종 기준이다. 새로고침 후 이전 탭 배열을 복원했다고 꾸미거나 0회로 자동 제출하지 않는다. 진행 중인 기록은 중단 확인/서버 조회를 제공한다.

시작 응답이 불명확할 때 같은 idempotency key를 보존한다. 종료 응답이 실패하면 메모리의 동일 payload만 재전송할 수 있다. 새로고침 후 이 임시 payload는 없어지고 서버의 확정 상태만 복구한다. 자격증명/탭 원본은 localStorage/sessionStorage에 저장하지 않는다.

## 구현 API와 검증 경계

[결과 OpenAPI](results.openapi.yaml)를 따른다. 전부 /api/v1의 인증된 경로이고 모든 POST에는 CSRF가 필요하다.

| Method | 경로 | 동작 |
| --- | --- | --- |
| GET | /sync/questions | 현재 문항과 id/label. 점수표는 제출값으로 받지 않음 |
| GET | /sync/types | 현재 버전 유형 설명 |
| GET | /sync/me | 본인 최초 결과 또는 result:null |
| POST | /sync/submit | 현재 버전 선택지로 서버 최초 결과 저장 |
| GET | /tap/me | 본인 공식 슬롯/현재 도전 복구 |
| POST | /tap/start | Idempotency-Key로 준비 시작 |
| POST | /tap/cancel | 시작 전만 기회 복구, 이후는 중단 |
| POST | /tap/finish | 서버 이벤트 검증과 최초 확정 |
| GET | /tap/leaderboard | 현재 유효 상위 10명과 본인 순위·소감 권한 |
| POST | /tap/record-message | 최고 기록 갱신 소감 100자, 최초 1회·동일 요청 재시도 |

공개 순위·소감과 관리자 소프트리셋은 구현되어 있으며 외부 AI 검수는 사용하지 않는다. 실제 휴대전화의 연타/확대/스크롤/가려짐, 행사 네트워크 지연과 부하, Mattermost 권한/DM은 자동 테스트와 구분해 검증해야 한다. 브라우저 API 대역/테스트 시간 제어는 운영 우회 기능이 아니다.
