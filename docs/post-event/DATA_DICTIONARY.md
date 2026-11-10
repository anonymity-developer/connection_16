# 참여 활동 데이터 정의서 — v1 / 2026-11-08 KST

이번 증분은 **퍼널 대시보드·전환율·이탈 분석·인사이트를 구현하지 않는다.** 관리자 결과 요약과 제한 조회는 구현하고, 개인별 분석 CSV는 승인 전 실제 추출·반출 경로가 없다. 실제 계정/운영 DB/개인정보를 읽지 않았다.

## 분석 CSV 6개 컬럼

| 컬럼 | 정의 |
| --- | --- |
| participant_id | `p_` + HMAC-SHA256(비공개 32바이트 이상 키, `분석기간ID + NUL + 내부 user_id`). 동일 키·기간 내 동일, 키/기간 변경 시 다름. 단순 SHA/순번 대체 안 함 |
| event_type | 아래 허용 이벤트 enum. 존재하지 않는 방문/시작을 생성하지 않음 |
| mission_code | SHOW_ME / SYNC_ME / MAKE_A_WISH / FAST_TAP / SAY_SOMETHING 또는 빈 값. 표시명 SAY 파이팅!이어도 내부 식별자 유지 |
| event_time | 실제 서버 DATETIME(6), UTC ISO8601. 마이크로초를 원본대로 보존. 스테이징 JDBC Timestamp→UTC 직렬화 필요 |
| status | 아래 원장에 저장된 상태. **대부분 조회 시점의 현재 상태**이며 과거 시점 상태를 추정하지 않음 |
| source_type | 시각이 나온 실제 테이블.컬럼. 원문·토큰·외부 계정·IP 없음 |

HMAC는 가명화이며 익명화/재식별 불가능을 보장하지 않는다. 키를 데이터셋/로그/저장소/Notion에 넣지 않는다. 키 보관 주체·접근·분석 목적·보관 기간·기간 간 연결 허용은 추가 승인 대상. 관리자 시상 자료의 `P-내부번호`와 분석용 HMAC ID는 서로 다른 용도다. 기본 집계 CSV에는 후보 검토에 필요한 P-ID와 완료 근거만 포함하며 참가자별 활동 원장은 포함하지 않는다.

## 실제 기록과 이벤트 정의

| event_type | 원장·시각 / 고유 record_key | status·해석 / 한계 |
| --- | --- | --- |
| ACCOUNT_CREATED | users.created_at / user:id | 현재 계정 상태. 가입 실패/OTP 요청 이력 아님 |
| PROFILE_FIRST_PUBLISHED | profiles.published_at / profile:user | 현재 PUBLIC/PRIVATE. 최초 공개 시각이며 소개·관심사가 나중에 완성됐다면 필수 프로필 완성 시각과 다름 |
| NFC_FIRST_UNLOCK | mission_progress.unlocked_at / unlock:user:mission | 현재 미션 상태. 최초 해금만, NFC 재스캔/물리 스캔 횟수 아님. SHOW_ME 제외 |
| SYNC_SUBMITTED | sync_results.submitted_at / sync:user | 최초 결과 STORED. 설문 페이지 진입/중간 선택 기록 없음. 완료 글자는 별도 LETTER_GRANTED |
| WISH_SUBMITTED | content_revisions.submitted_at / revision:id | 소원 수정본별 접수. 현재 moderation_status; 최초·수정은 서로 다른 revision |
| CHEER_SUBMITTED | 같은 원장 / revision:id | 독립 응원 수정본. 소원 댓글과 다름 |
| COMMENT_SUBMITTED | 같은 원장, kind=COMMENT / revision:id | 소원 댓글 수정본별 접수. 임의로 SAY_SOMETHING 미션 코드 부여 안 함 |
| MODERATION_DECISION | moderation_reviews.created_at / review:id | 해당 리뷰의 실제 decision. reason/원문/운영자 식별자는 미포함. 관리자 조치 감사 로그와 다른 데이터 |
| TAP_SCHEDULED | tap_attempts.starts_at / tap-start:id | 서버가 예약한 5초 준비 이후 플레이 시작 시각. 요청 접수 시각·실제 첫 터치·끝까지 플레이했다는 근거 아님. 취소돼도 예약 시각은 남음 |
| TAP_SUBMITTED | tap_attempts.submitted_at / tap-submit:id | 실제 공식 제출 저장 시각과 현재 VALID/REVIEW_REQUIRED 등 상태. 단순 VIEW/중단 시각으로 추정하지 않음 |
| LETTER_GRANTED | mission_progress.completed_at / grant:user:mission | 서버 지급 완료 시각. 검수 지연 시 원본 접수 시각과 다르며 최초 완성상은 기존 인정 접수 근거를 사용 |
| ALL_COMPLETED | event_completions.completed_at / complete:user | 실제 전체 완성 기록. 기존 순번은 시상 순위로 바꾸지 않음 |
| HEART_CURRENT | cheer_likes.created_at / heart:card:user | 현재 남은 하트. 취소 시 행이 삭제되어 과거 누른·취소 횟수는 복원 불가. 숨김/자기 하트 등 유효성은 별도 공개 집계 기준과 구분 |
| PROFILE_FIRST_VISIT | profile_views.first_viewed_at / visit:viewer:viewed | 방향별 최초 관계. 재방문 횟수·체류 시간 없음. CSV는 상대방 ID를 내보내지 않음 |

record_key는 승인 후 추출 시 메모리/격리 스테이징에서만 사용하고 6컬럼 CSV에는 남기지 않는다. `(internal_user_id, record_key)`로 동일 원장 중복만 제거하며 같은 key의 상충 값은 오류다. 서로 다른 원장이 우연히 6컬럼에서 같아 보여도 삭제하면 안 된다. pandas의 `drop_duplicates()`를 임의 적용하지 않는다. 스캔→수행→완료가 반드시 순서대로 기록된다고 가정하지 않는다.

## SQL 설계와 실행 경계

- 실제 구조와 맞춘 설계: `backend/src/test/resources/post-event/activity-extract.sql`. MySQL8 UNION ALL SELECT만 존재하며 NamedParameterJdbcTemplate 격리 테스트에서만 호출한다. 운영용 endpoint/DB 접속 CLI/다운로드 버튼이 없다.
- `from_at <= event_time < until_at`, `event_time <= observed_at`. 입력 시각 모두 UTC, from<until<=observed 검증과 승인된 기간 설정이 실제 활성화의 선행 조건이다.
- 설계 cohort는 조회 시점 ACTIVE·USER, account.created_at<=observed. ADMIN/탈퇴/정지 제외. 보관돼 있더라도 삭제/철회 이전 과거 cohort를 재구성하지 않는다. 향후 승인 정책이 다른 cohort를 요구하면 별도 변경한다.
- 승인 후에도 내부 ID/record_key는 CSV 생성 이전 HMAC 변환·삭제해야 한다. 실제 SQL 결과를 그대로 CSV로 복사하는 기능은 제공하지 않는다.
- 현재 CSV CLI는 **내장 가상 fixture만 생성**한다. `activity_dataset.transform()`은 순수 계약 테스트용 변환 함수이며 실제 DB 연결/반출을 활성화하지 않는다.
- 현재 SQL 추출 설계는 DB 통합 미검증이다. Python 가상 fixture PASS를 MySQL 쿼리 PASS로 해석하지 않는다.

## 기록되지 않는 항목 — 집계 불가

페이지 방문/체류/중간 설문 이탈, NFC 총 스캔/재시도 횟수, 취소된 하트 전체 이력, 실제 게임 시작 요청 접수 시각 및 모든 배경 전환 시각, 프로필 필수 항목이 최초로 모두 충족된 정확한 시각, S 재평가 시도 횟수, 물리 NFC 터치 여부. 새 수집·추적·보관 정책 및 스키마는 구현하지 않았다. 기존 감사 로그는 관리자 판정/숨김/재처리용이며 참가자 행동 추적 로그로 재사용하지 않는다.

## 관리자 집계와 분석용 데이터의 차이

- 결과 화면 통계: **현재 ACTIVE·USER의 누적 원장**, 관찰 시각까지. 사전 등록 포함. 행사 설정 시각은 함께 표시하되 모든 누적 통계가 행사 기간 필터라고 주장하지 않음. 가입/활동/완료 각 고유 ID, 단계별 수치는 독립 상태이며 전환율 미계산.
- 공개 소원·응원·댓글: 현재 공개 자격·최신 동의·VISIBLE·유효 승인본. 타인 댓글은 공개 원글까지 확인. 옛 승인본을 유지한 재검수의 경우 승인본은 공개, 수정본은 대기로 각각 집계.
- 검수 분포: 수정본별 현재 작업 상태(한 작성자의 여러 버전 가능). 미션 pending은 현 pending pointer의 고유 작성자. 전체 미처리 경고는 숨김/탈퇴를 포함한 운영 큐 전체.
- 글자 분포: 등록자별 서버 COMPLETED 0~5개. 사후 공개 철회로 시상 부적격이 돼도 지급 원장을 임의 삭제하지 않는다. 기존 AwardCalculator가 검증한 5종 근거의 시상 인원은 별도.
- TAP: 전체 시도 저장 상태와 현 슬롯 저장 상태, 현 슬롯 VALID 점수 25단위 구간을 분리. 공식 도전 행 존재 인원은 실제 터치 인원이 아님. 시상은 기존 서버 근거 계산만 재사용.
- 시간대 목록: UTC 1시간, 각 활동별 고유 user_id 수와 원장 건수; 최대 최근 1000개 버킷+생략 플래그. WISH_FIRST_SUBMISSION은 슬롯 최초 생성 시각, WISH_REVISION_SUBMITTED는 수정본 접수 시각으로 같은 활동의 서로 다른 정의. 합산하지 않음. 현재 남은 하트/방문도 누적 cohort 기준이며 현재 공개 가능한 관계만 세는 대표 지표와 분모가 다름.
- 방향 방문은 `(viewer,viewed)` PK. 상호 방문은 양방향 존재하며 작은 ID<큰 ID로 쌍 중복 제거. 관계 가중치 변경 없음.
- 한 결과 API의 전체 통계·시상은 동일 REPEATABLE_READ 읽기뷰. awardFacts의 SQL 시각 비교를 하나의 DB observedAt에 묶었다. 기존 AwardCalculator/규칙/지급/검수 동작은 바꾸지 않았다.
- 자료 지문은 **반환하는 자료 내용**에서 observedAt만 제외한 SHA256. 원본 DB 전체 감사/영구 확정 스냅샷이 아니다. 같은 지문이면 반환 수치/근거 동일, 재조회 시 주요 수치 차이를 표시. 내보내기는 최신 자료가 다르면409로 거절한다. 검토 기록100개·시간대1000개 생략 범위를 명시한다.

성향 분포의 유효성 표시는 등록 버전의 definition 파싱·해당 type_code 존재 여부 검사다. 원문 응답/점수를 읽거나 재계산하지 않으며 유형 존재만으로 전체 원장 무결성을 보장하지 않는다. 전체 지급/시상 자격은 기존 서버 검증을 별도로 따른다. 새 MySQL 통합 fixture는 운영 JDBC와 같은 UTC connection/session 옵션을 명시했다.
