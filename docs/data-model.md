# 데이터 모델과 마이그레이션 계획

## 1. 적용 범위

현재 실행 가능한 DDL은 `backend/src/main/resources/db/migration`의 V1/V2다. 콘텐츠, 게임, 검수, 시상은 아래 **후속 설계**이며 아직 테이블을 생성하지 않는다. 해당 도메인의 규칙과 함께 마이그레이션을 추가한다.

이 문서의 ERD는 핵심 요약이다. 모든 도메인의 컬럼/관계/복합키와 nullable 규칙은 [전체 도메인 ERD](erd.md)를 함께 읽는다. 현재 SQL과 후속 설계의 경계를 유지하고, 후속 설계를 현재 DB에 적용된 것으로 간주하지 않는다.

- MySQL 8.4 / InnoDB / utf8mb4
- 날짜는 UTC `DATETIME(6)`로 저장하고 UI에서 한국 시간으로 표시한다.
- 외부 계정 키와 서비스 사용자 키를 분리한다.
- 개인정보 삭제는 연결된 시상/운영 데이터 보관 정책까지 정한 뒤 명시적으로 구현한다. 무조건 cascade 삭제하지 않는다.
- 기획서의 단일 행사 운영을 기준으로 한다. 여러 행사 지원 시 event_id 및 복합 UNIQUE를 추가하는 별도 변경이 필요하다.

## 2. 현재 핵심 ERD

```mermaid
erDiagram
    USERS ||--o| PROFILES : owns
    USERS ||--o{ USER_INTERESTS : selects
    INTERESTS ||--o{ USER_INTERESTS : includes
    MISSIONS ||--o{ TAGS : unlocks
    USERS ||--o{ MISSION_PROGRESS : performs
    MISSIONS ||--o{ MISSION_PROGRESS : defines
    USERS ||--o| EVENT_COMPLETIONS : completes
    USERS ||--o{ PROFILE_VIEWS : viewer
    USERS ||--o{ PROFILE_VIEWS : viewed

    USERS {
        bigint id PK
        varchar mattermost_id UK
        varchar real_name
        varchar region
        int class_no
        varchar role
        varchar status
    }
    PROFILES {
        bigint user_id PK,FK
        varchar nickname
        varchar bio
        boolean is_public
        datetime published_at
    }
    MISSIONS {
        bigint id PK
        varchar code UK
        char letter
        int sort_order UK
        boolean active
    }
    TAGS {
        bigint id PK
        bigint mission_id FK
        char token_hash UK
        boolean active
    }
    MISSION_PROGRESS {
        bigint user_id PK,FK
        bigint mission_id PK,FK
        varchar status
        datetime unlocked_at
        datetime completed_at
    }
    EVENT_COMPLETIONS {
        bigint user_id PK,FK
        bigint completion_order UK
        datetime completed_at
    }
    PROFILE_VIEWS {
        bigint viewer_id PK,FK
        bigint viewed_id PK,FK
        datetime first_viewed_at
    }
```

## 3. 현재 적용된 테이블

| 테이블 | 키 및 주요 제약 | 의미 |
| --- | --- | --- |
| users | PK id, UNIQUE mattermost_id, role/status CHECK | 인증된 참여자; ADMIN은 서버 운영 절차로만 부여 |
| profiles | PK/FK user_id | 사용자 1명에 프로필 최대 1개, 공개 기본값 false |
| interests | UNIQUE name | 관심사 사전 |
| user_interests | PK(user_id, interest_id) | 관심사 중복 방지 |
| missions | UNIQUE code, UNIQUE sort_order | 두 S를 구분하는 고정 미션 코드 |
| tags | UNIQUE token_hash | 원문 토큰을 저장하지 않음 |
| mission_progress | PK(user_id, mission_id), 상태/완료일 CHECK | 행 없음=LOCKED, 이후 UNLOCKED/IN_PROGRESS/COMPLETED |
| event_completion_sequence | PK id, id=1 CHECK | 전역 완료 순번 발급용 singleton |
| event_completions | PK user_id, UNIQUE completion_order | 전체 완성 최초 1회; 최종 시상 순위와 분리 |
| profile_views | PK(viewer_id, viewed_id), self-view CHECK | 최초 상세 방문, 역방향 조회용 인덱스 포함 |

프로필은 회원 생성 직후 없을 수 있어 관계가 0..1이다. 닉네임은 UNIQUE가 아니다. 관심사 초기 목록과 실제 NFC 토큰은 운영 확정 전 임의로 시드하지 않는다. V2에는 다섯 미션과 완료 sequence 초기값만 있다.

## 4. 후속 콘텐츠/검수 ERD

다음 구조는 수정본 재검수와 이전 버전 응답 경합을 처리하기 위한 계획이다.

```mermaid
erDiagram
    USERS ||--o{ CONTENT_ITEMS : authors
    CONTENT_ITEMS ||--o{ CONTENT_REVISIONS : versions
    CONTENT_REVISIONS ||--o{ MODERATION_JOBS : reviews
    CONTENT_ITEMS ||--o| WISHES : wish
    CONTENT_ITEMS ||--o| CHEER_CARDS : cheer
    CONTENT_ITEMS ||--o| WISH_COMMENTS : comment
    WISHES ||--o{ WISH_COMMENTS : receives
    CHEER_CARDS ||--o{ CHEER_LIKES : receives
    USERS ||--o{ CHEER_LIKES : likes
```

| 테이블 | 필수 컬럼/제약 및 인덱스 |
| --- | --- |
| content_items | id PK, author_id FK, kind, visibility_status, approved_revision_id, pending_revision_id, created_at, updated_at, optimistic version |
| content_revisions | id PK, item_id FK, revision_no, text, presentation_json, submitted_at, moderation_status; UNIQUE(item_id, revision_no) |
| wishes | id PK, user_id UNIQUE FK, content_item_id UNIQUE FK, luck_score, landing_zone; 운 결과는 슬롯에 최초 저장 |
| wish_comments | id PK, wish_id FK, author_id FK, content_item_id UNIQUE FK, reveal_profile; INDEX(wish_id,id), INDEX(author_id,wish_id) |
| cheer_cards | id PK, user_id UNIQUE FK, content_item_id UNIQUE FK, anonymous; 디자인 값은 revision에 저장해 함께 검수 |
| cheer_likes | PK(card_id,user_id), created_at; 카드/사용자 FK |
| moderation_jobs | id PK, revision_id FK, job_key UNIQUE, status, attempts, next_run_at, lease_until, result_json, provider, policy_version, last_error_code |
| moderation_reviews | id PK, revision_id FK, reviewer_id, decision, reason, created_at; 자동/관리자 판정 이력 |

`content_items` 생성 → revision 생성 → 포인터 갱신 순으로 저장한다. 포인터 FK는 두 테이블 생성 후 ALTER로 추가한다. revision의 본문/스타일은 수정하지 않고 새로 삽입하며 검수 상태 메타데이터만 갱신한다. 포인터는 같은 item의 revision만 참조하고 도메인 작성자와 content_items.author_id가 일치해야 한다. 외부 입력 `presentation_json`은 허용 속성만 저장한다. AI 응답 원문을 무제한 보관하지 않고 필요한 분류/사유만 구조화한다.

익명 여부는 API serializer에서 강제한다. 내부 author_id가 있다는 이유로 공개 JSON에 그대로 직렬화하지 않는다.

## 5. 후속 게임/결과/시상

| 테이블 | 주요 컬럼/제약 |
| --- | --- |
| sync_results | user_id PK/FK, questionnaire_version, answers_json, axis_scores_json, type_code, submitted_at |
| tap_slots | user_id PK/FK, current_attempt_id, version; 공식 참여 권리 하나 |
| tap_attempts | id PK, user_id FK, generation, idempotency_key UNIQUE, starts_at, ends_at, submit_deadline, status, tap_count, submitted_at, payload_hash; UNIQUE(user_id,generation) |
| tap_attempt_events | id PK, attempt_id FK, type, relative_time_ms, payload_json, created_at; 원본 이벤트/중단/관리자 조치 이력 |
| submission_receipts | id PK, user_id FK, source_kind, source_id, received_at; UNIQUE(user_id,source_kind,source_id), 최초 서버 접수 시각/순서. 후속 설계이며 현재 V1/V2에는 없음 |
| mission_completion_evidence | user_id/mission_id PK/FK, source_kind, source_id, accepted_received_at, receipt_order UNIQUE FK → submission_receipts.id; 최초 유효 근거 |
| award_runs | id PK, cutoff_at, finalized_at, rules_version, status, created_by; 정산 실행 snapshot |
| awards | id PK, award_run_id FK, award_type, user_id FK, rank, evidence_json, status; UNIQUE(run,type,rank) |
| content_reports | id PK, reporter_id FK, content_item_id FK, reason, status, resolved_by, resolved_at |
| event_settings | singleton PK, opens_at, submit_closes_at, moderation_closes_at, game_duration, settings_version |
| admin_audit_logs | id PK, actor_id, action, target_type/id, reason, before_json, after_json, created_at |
| activity_events | id PK, user_id nullable, event_name, target_id, occurred_at; 보관 기간/수집 범위 확정 후 추가 |
| user_consents | id PK, user_id FK, policy_type, policy_version, agreed_at, withdrawn_at; 동의 증빙 |

`tap_attempts.user_id UNIQUE`를 사용하면 관리자 승인 재도전 이력을 보관할 수 없으므로 슬롯과 도전 기록을 나눈다. 정산 근거의 접수 순서는 서버에서 발급하고 클라이언트 시각을 사용하지 않는다.

submission_receipts는 제출을 받는 같은 트랜잭션에서 생성하며 승인 시 새로 만들지 않는다. 콘텐츠 source_id는 revision ID, 성향/게임은 결과 또는 attempt ID처럼 종류별로 유일한 원본을 참조한다. 여러 테이블을 가리키는 source_kind/source_id는 단일 FK로 보장할 수 없어 소스 존재/소유자/미션 일치를 서비스에서 검증한다. SHOW ME를 먼저 작성했다면 이후 태그 해금 접수가 유효 완료 근거가 된다. 상세 규칙은 ERD의 접수/시상 항목과 구현 계획을 따른다.

## 6. 쿼리 규칙

프로필 방문 관계는 `(viewer_id, viewed_id)` 정방향 PK와 `(viewed_id, viewer_id)` 인덱스를 사용한다. 상호 방문은 반대 행의 존재 여부로 계산한다. 사용자/프로필을 join하여 ACTIVE 및 공개 상태를 검사한다.

댓글 활동상은 승인된 revision과 공개/유효한 소원을 대상으로 `COUNT(DISTINCT wish_id)`를 집계하며 자신의 소원은 제외한다. 문자열 status 하나만 보지 않고 현재 공개 포인터와 운영 숨김 상태를 함께 확인한다.

대량 목록은 커서와 limit 상한을 사용한다. N+1 조회를 피하도록 DTO projection 또는 명시적 fetch 쿼리를 사용하며, 성능 개선은 EXPLAIN과 실제 데이터 측정 후 진행한다.

## 7. 마이그레이션 규칙

1. V1 핵심 스키마, V2 미션 시드는 이미 초기 구성에 포함한다.
2. 다음 버전은 인증 동의 기록/프로필 규칙에 맞춰 추가한다.
3. 콘텐츠/revision/검수, 게임, 시상 순으로 해당 기능과 함께 추가한다.
4. 적용한 파일은 수정하지 않는다. 변경은 새 V 파일로 추가한다.
5. 데이터 삭제나 컬럼 제거 전에는 백업/복원과 애플리케이션 호환성을 확인한다. 애플리케이션 롤백만으로 DDL이 되돌아가지 않는다.
