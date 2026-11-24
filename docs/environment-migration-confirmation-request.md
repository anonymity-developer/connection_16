# 환경 관리자에게 전달할 확인 요청문 — 발송하지 않은 초안

TAG ME Flyway 통합을 준비하고 있습니다. 권한이 있는 담당자께서 해당 환경을 **읽기 전용**으로 확인해 주세요. 데이터·권한·Flyway 이력 변경, repair/baseline/clean, migration 실행은 요청하지 않습니다.

확인 대상은 #37의 `V21__add_mission_funnel_events.sql`과 P0의 `V23__add_page_event_analytics.sql`입니다.

1. 환경명과 확인 시각, 기존 DB/schema를 알려 주세요. 접속 정보·비밀번호·참가자 행·DB 덤프는 필요하지 않습니다.
2. 두 파일 또는 같은 SQL을 이 환경에 적용한 이력이 있는지 **있음/없음/모름**으로 답해 주세요. 이미 삭제·정정한 이력이 있다면 그 사실도 구분해 주세요.
3. Flyway V21–V24와 관련 파일의 `version`, `script`, `checksum`, `success`만 확인해 주세요. 같은 V21이라도 main TAP 복구인지 퍼널인지 파일명으로 구분해야 합니다.
4. `funnel_collection`, `mission_funnel_events`, `page_event_collection`, `page_events`와 `mission_funnel_events.analytics_session_key` 존재 여부를 확인해 주세요.
5. Flyway 외 수동 SQL 또는 별도 파이프라인에서 같은 CREATE/ALTER를 적용한 적이 있는지도 확인해 주세요.

기존 SELECT 권한으로 조회가 불가능하면 권한을 새로 부여하지 말고 ‘조회 불가’로 답해 주세요. 관리자 확인 전에는 재번호·기존 이력 수정을 진행하지 않겠습니다.

## 담당자가 검토할 읽기 전용 SQL 예시

담당자가 이미 승인된 접속과 올바른 DB/schema를 선택한 세션에서만 사용합니다. 에이전트는 운영/공유 환경에 연결하거나 이 초안을 발송하지 않습니다.

```sql
SET SESSION MAX_EXECUTION_TIME = 5000;
SET SESSION TRANSACTION READ ONLY;
START TRANSACTION;
SELECT DATABASE() AS database_name, @@session.transaction_read_only AS read_only;

SELECT version, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('21','22','23','24')
   OR script LIKE '%mission_funnel%'
   OR script LIKE '%page_event%'
ORDER BY installed_rank;

SELECT table_name
FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name IN ('funnel_collection','mission_funnel_events',
                     'page_event_collection','page_events');

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND table_name='mission_funnel_events'
  AND column_name='analytics_session_key';
ROLLBACK;
```

기존 Flyway 메타데이터나 테이블이 없으면 그 결과를 그대로 알려 주세요. 이번 확인을 위해 새로 만들지 말아 주세요.
