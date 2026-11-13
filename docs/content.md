# 소원·응원 제출과 공개

현재 기본은 **검수 없이 즉시 공개**다. 소원·응원·댓글은 참가·입력 검사를 통과한 트랜잭션에서 `APPROVED / DIRECT_PUBLICATION`으로 공개하며 새 검수 job이나 외부 호출을 만들지 않는다. AI·관리자가 검수했다는 뜻이 아니다. 아래 비동기 검수 구조는 `tagme.moderation.enabled=true`를 명시할 때만 사용하는 후속 선택 기능이다. [현재 공개·초기화 계약](direct-publication-and-reset.md).

이 문서는 구현된 서버/화면 계약이다. 경로는 `/api/v1` 기준이며 모든 조회는 로그인 세션, 변경은 추가로 CSRF 토큰이 필요하다. 관리자 API는 현재 ADMIN 역할과 유효한 세션을 모두 확인한다.

## 구현 범위

MAKE A WISH와 SAY SOMETHING의 최초 작성, 내 슬롯, 수정, 승인된 공개 목록, 비동기 검수, 관리자 판정 API를 구현한다. 댓글/하트는 페이즈 4-3의 [반응 계약](reactions.md)으로 확장했다. 관리자 검수 화면/커서/재처리와 콘텐츠 숨김·복구/감사는 [페이즈 5-2](operations-admin.md)에 연결했다. 일반 공개 상세 조회/참가자 신고/외부 검수 업체 연동/시상 정산은 후속 범위다. DB에는 참여자나 문구, 운 정책을 시드하지 않는다.

검수 모드에서 외부 검수 업체가 선택되지 않은 `ModerationProvider`는 `REVIEW_REQUIRED / PROVIDER_NOT_CONFIGURED`만 반환한다. 승인처럼 보이는 테스트 대역은 통합 검사에만 존재한다. 이는 검수 모드를 켠 경우의 동작이며 현재 기본 모드에서는 제공자를 호출하지 않는다. 외부 AI 호출이나 실제 제출/배포를 수행했다는 의미가 아니다.

## 쓰기와 1회 슬롯

- `content_items`는 WISH/CHEER 각각 한 슬롯을 보장한다. V6의 직접 UNIQUE는 V7에서 동일한 의미의 `(author_id, slot_kind)` generated key UNIQUE로 확장해 COMMENT만 다중으로 허용한다. 수정은 같은 슬롯의 새 revision이다.
- 쓰기마다 사용자 잠금 → 행사/활성 미션/해금/현재 공개 프로필 및 동의 → 슬롯/수정 조건을 검사한다. 최초 작성은 POST, 기존 슬롯 수정은 PUT이며 `expectedVersion`이 현재 슬롯 버전과 같아야 한다.
- 소원·응원 본문은 공백 제외 1~400 UTF-16 code unit이다. 댓글은 200자를 유지한다. 앞뒤 공백만 제거하고 저장한다. 클라이언트 HTML/CSS, 사용자 ID, 점수, 판정 필드는 받지 않는다.
- WISH는 `PLAIN / ROSE`를 사용한다. CHEER 신규 작성/수정은 `PLAIN`으로 통일하고 `ROSE|INK|PEACH|LEMON|MINT|SAGE|LAVENDER|LILAC|SAND|CLOUD` 10색을 선택한다. 기존 NOTE revision은 보존하되 목록에서는 기본 카드로 표시한다. 이전 NOTE 요청의 같은 키 재전송도 유지한다. 둘 다 익명 또는 닉네임 공개를 선택할 수 있다. 본문과 카드 형태/색상/익명 여부는 revision의 일부이며 공개본과 함께 전환된다.
- 같은 슬롯에서 최대 20개 revision을 저장한다. 사용자별 쓰기 30회/분 제한과 실제 DB 제약을 함께 사용한다.
- `Idempotency-Key`는 16~64자의 영문/숫자/`_`/`-`다. `(item_id, key)`를 보관하고 동일 본문/카드 설정은 현재 슬롯 상태를 반환한다. 같은 키/다른 내용은 409다. 새 키의 POST로 두 번째 슬롯을 만들 수 없다.
- `expectedVersion`은 내용 해시에서 제외한다. 이미 저장된 요청의 재전송을 상태 변화 뒤에도 확인할 수 있게 하기 위함이다. 저장된 본문/설정과 다르면 재전송으로 인정하지 않는다.
- 동일 요청의 응답이 유실되면 프론트는 입력을 잠그고 동일 키/본문으로 재시도하거나 내 슬롯을 다시 조회한다. 페이지 새로고침 후에는 내 슬롯 조회로 복원한다. 초안 본문/키를 브라우저 영구 저장소에 남기지 않는다.

접수 시각은 사용자 잠금 대기 전에 조회한 DB UTC 시각이다. 실제 HTTP 네트워크 도착 순서를 입증하는 값은 아니다. 저장 시에도 행사 제출 기간을 검사한다. item, 불변 revision, job, receipt와 최초 A/Y 획득을 한 MySQL 트랜잭션에 저장하고 202를 반환한다. 공개 여부는 별도로 결정한다.

## 버전과 공개 포인터

`approved_revision_id`와 `pending_revision_id`를 구분한다. 복합 FK `(revision_id, item_id)`는 같은 item의 revision만 참조하도록 막는다. 수정 중에는 기존 승인본을 유지한다. 거절되면 pending 포인터를 비우고 latest revision에 거절 상태를 남긴다. 기존 승인본과 글자는 지우지 않는다.

기존 대기본을 수정하면 그 작업과 revision은 `SUPERSEDED`가 되고 fence가 증가한다. 새 revision/job이 생긴다. 처리 중이던 이전 요청이 돌아와도 pending 포인터/fence가 맞지 않아 아무 것도 바꾸지 않는다. 본문/카드 속성은 UPDATE하지 않고 새 revision으로만 추가한다.

슬롯 version은 수정안 저장과 승인/거절 포인터 변경 때 증가한다. 오래된 화면의 수정은 409이며 새 상태 조회 후 다시 작성한다. worker claim/단순 재검토 상태는 포인터가 바뀌지 않으므로 슬롯 version을 올리지 않는다.

## 검수 작업과 장애

1. worker는 DB 작업 하나를 `FOR UPDATE SKIP LOCKED`로 claim한다. 대기 작업 또는 lease가 만료된 처리 작업만 선택한다.
2. claim마다 attempts/fence를 증가시키고 30초 lease를 발급한다. 트랜잭션을 끝낸 후 `ModerationProvider.inspect(text)`를 호출한다. 계정/실명/지역/반/스타일은 외부 경계에 전달하지 않는다.
3. 전화번호/이메일/HTTP URL 패턴이 감지되면 제공자 호출 없이 운영자 확인으로 보낸다. 이 규칙이 모든 개인정보를 감지한다는 보장은 없다.
4. 결과 반영은 작성자 잠금 → item/job 잠금 → pending 포인터/현재 fence/미만료 lease 확인 순으로 진행한다. 오래된 응답과 중복 응답은 무시한다.
5. 제공자의 상태는 APPROVED/REJECTED/REVIEW_REQUIRED만 허용한다. 사유/provider/policy는 길이가 제한된 토큰으로 확인한다. null/이상 응답은 운영자 확인으로 보낸다.
6. 명시적인 일시 장애는 10초, 20초 뒤 재시도하며 3회 실패하면 REVIEW_REQUIRED다. lease 회수도 attempts에 포함하고 3회 이후 새 claim은 제공자를 다시 호출하지 않는다. 그 외 장애는 즉시 REVIEW_REQUIRED다.
7. 프로세스 중단 시 DB에 job이 남아 lease 만료 후 다시 claim한다. 재시도는 승인된 공개본을 덮어쓰지 않는다.

기본 worker는 2초 간격으로 작업 하나를 처리한다. 필요 시 `tagme.content.worker-enabled=false`로 멈추고 `tagme.content.poll-ms`로 간격을 조절한다. 처리량과 제공자 timeout은 실제 업체를 연결할 때 부하/지연을 검증해야 한다. lease 30초보다 긴 제공자 호출은 결과가 버려지며 검토/재시도 정책을 따라야 한다.

예외 원문/외부 응답 원문/본문/계정 정보는 job 에러 필드나 자체 로그에 넣지 않는다. 판정 사유와 provider/policy 및 관리자 판정 이유는 별도로 제한된 필드에 저장한다.

## 승인과 완료 근거

자동/관리자 승인은 계정 ACTIVE, 현재 공개 프로필/동의, 미션 활성/해금, 콘텐츠 비숨김, 검수 가능 기간을 다시 확인한다. 조건이 바뀌면 승인하지 않고 `PARTICIPATION_UNAVAILABLE`로 운영자 확인 상태를 유지한다.

글자는 NFC로 해금한 뒤 유효한 제출을 저장할 때 지급한다. CompletionRule은 본인의 현재 대기 revision과 서버 receipt를 확인한다. 이전 정책에서 저장한 글은 최초 승인 시 지급할 수 있다. 수정·재승인·재시도는 지급 근거를 바꾸지 않는다. 시상 검증은 현재 공개 상태와 최초 승인된 버전의 유효한 접수 시각을 사용한다.

검수는 제출 마감 뒤에도 `moderation_closes_at`까지 처리할 수 있다. 이때 새 접수를 만들지 않고 기존 receipt가 행사 시작 이상/제출 마감 미만인지 검사한다. 전체 완료 순번은 여전히 운영 처리 순번이며 최종 선착순 수상 순위가 아니다. 최종 정산은 별도 구현한다.

## 운 점수

선택적인 `content_settings(id=1)`의 `wish_luck_min/max`, `wish_zone_count`는 운영자가 검토하여 설정한다. 범위는 0~10000, 구역 수는 1~20이다. 설정이 있을 때만 최초 소원 슬롯 생성 시 SecureRandom으로 한 번 정해 저장한다. 승인 전에는 본인/공개 응답에도 결과를 주지 않는다. 수정/재접속으로 재추첨하지 않는다.

미설정이면 luck/zone은 null이며 점수를 만들거나 보여주지 않는다. 이 상태로 이미 생성된 슬롯에 정책을 나중에 설정해도 자동 추첨하지 않는다. 운 점수의 시상 사용과 범위는 아직 확정 정책이 아니다.

## 익명과 현재 공개 상태

공개 목록은 승인된 포인터/APPROVED, 비숨김, 작성자 ACTIVE, 공개 프로필 및 현재 두 동의 버전을 SQL에서 함께 필터링한다. 최신 pending/거절본은 공개 목록에 절대 섞지 않는다.

공개 JSON은 콘텐츠 ID/본문/카드 설정/익명 여부/표시 닉네임/운 결과와 nullable profileId를 반환한다. 승인된 비익명 소원·응원이고 작성자가 현재 공개 가능한 경우에만 nickname/profileId를 제공한다. 익명일 때 둘 다 null이다. 기존 익명 소원도 작성자의 명시적 변경 전에는 익명으로 유지한다. revision ID/접수 시각/해시/Mattermost ID는 반환하지 않는다. 콘텐츠 자체의 ID는 목록 식별자이며 프로필 ID가 아니다. 프로필 링크는 [탐색 계약](discovery.md)의 CSRF POST 상세로 연결한다. 수정 대기 중에는 기존 승인본의 익명 여부를 따른다.

내 슬롯에는 승인본/대기본/최신본과 수정용 버전이 포함된다. 다른 사용자의 ID로 내 슬롯을 지정하는 파라미터는 없다. 공개 응답/내 상태는 no-store다. 프로필 공개 철회나 신규 동의 버전 적용 후 조회에서는 현재 조건에 맞지 않는 글을 제외한다. 과거 글자/증빙 기록을 개인정보 공개 상태와 함께 지우지 않는다.

목록은 ID 내림차순으로 20개이며 다음 cursor가 있을 때 더 불러온다. 승인 시각 순위/작성 시각 순위를 의미하지 않는다.

## 관리자 API

- GET `/admin/moderation`: 최대 50건의 QUEUED/PROCESSING/REVIEW_REQUIRED 작업, 해당 비공개 revision과 기존 승인본을 반환한다. 일반 사용자에게는 403이다.
- POST `/admin/moderation/{jobId}/decision`: `{expectedFence, decision:APPROVED|REJECTED, reason}`. reason은 1~300자로 필수다. 처리 전에 관리자/작성자 ID를 오름차순으로 잠근다.
- 현재 pending과 fence가 다르거나 이미 처리한 작업은 409다. 중복 판정 요청으로 추가 승인/글자를 만들지 않는다. manual fence 증가가 늦은 자동 응답을 무효화한다.
- 성공은 204이며 관리자 ID/판정/사유/시각/fence를 `moderation_reviews`에 저장한다. APPROVED 요청이라도 참가 조건이 사라졌으면 REVIEW_REQUIRED로 남으며 실제 결과는 목록/내 슬롯에서 다시 확인한다.

페이즈 5-2는 `/admin` 화면과 GET `/admin/moderation/tasks` 커서 목록, 제한 재처리, 콘텐츠 승인본/대기본·숨김/복구와 감사 조회를 추가했다. 기존 최대 50건 배열 GET은 호환 유지한다. 판정의 requestedDecision과 실제 상태를 V8 admin_audit_logs에 기록하며 감사 실패 시 판정/글자도 롤백한다. 참가자 신고/계정 제재/게임 검토/시상은 후속이다. 정확한 조건과 한계는 [운영 계약](operations-admin.md)을 따른다.

## 검증 범위

실제 MySQL/Redis Testcontainers로 슬롯 동시 생성, 멱등 충돌, 불변 버전, 오래된 worker/fence/lease, 제한 재시도, 공개/익명 필터, 승인 후 수정 거절, 관리자 권한/CSRF, 제출·승인 롤백, 제출 마감 뒤 원접수 근거를 검사한다. 브라우저 계약 대역은 폼/스타일/익명 미리보기/미응답 재전송/오래된 수정/검수 대기/복원/미해금과 5개 폭을 확인한다. 실제 업체 품질/실제 기기/운영 네트워크 검사와 구분한다.

## V7 댓글 확장

kind=COMMENT와 다중 댓글은 V7 generated slot key로 추가한다. 승인/거절 포인터와 job은 재사용하지만 댓글에는 미션 완료를 호출하지 않는다. 댓글과 부모 작성자, 관리자를 정렬해 먼저 잠그며 부모의 현재 공개 조건을 함께 확인한다. V6 자체를 수정하지 않았고 상세 계약은 [반응 계약](reactions.md)이다.

## 외부 어댑터 추가 (feat/network-graph)

GMS/OpenAI 선택 설정과 실패 시 REVIEW_REQUIRED 처리, 실연동 미검증 경계는 [작업 보고](network-moderation-handoff.md)를 따른다. 기본 NONE은 기존 수동 검수를 유지한다. OpenAI 단독 unflagged는 자동 승인이 아니며 추가 정책 검증이 필요하다.

## 2026-11-13 응원 목록·캐시 계약

`GET /cheers?sort=LATEST|HEARTS&direction=ASC|DESC&cursor=...`는 한 번에 최대 20개와 다음 커서를 반환한다. LATEST는 최초 작성 ID 순서이며 수정으로 순서가 바뀌지 않는다. HEARTS는 현재 유효한 하트 수, 동점은 작성 ID를 같은 방향으로 정렬한다. 커서는 정렬·방향·첫 조회 최대 ID·직전 ID/하트 수를 포함하며 잘못된 값은 400이다. 기본은 LATEST/DESC이며 이전 숫자 커서는 이 기본 조합에서만 지원한다. 소원 목록의 숫자 커서와 API는 유지한다.

프런트는 20개씩 ‘응원 더 보기’로 추가하고 이미 읽은 ID는 중복 표시하지 않는다. 정렬 변경/새로고침은 첫 페이지로 돌아가며 늦게 도착한 이전 정렬 응답은 버린다. 하트 숫자는 즉시 반영하되 카드 위치는 변경하지 않는다. 페이지 간 동시 하트/공개 상태 변경은 다음 쿼리에 반영되므로 전체 목록의 시점 고정 스냅샷은 아니며, 변경 중 경계가 이동한 항목은 새로고침으로 다시 조회한다.

목록 본문·작성자·유효 하트 집계를 Redis/공용 캐시에 저장하지 않는다. 현재 화면의 페이지/하트 상태를 메모리에 유지하고 같은 다음 페이지의 동시 요청은 합친다. 작성·수정·명시적 새로고침·정렬 변경 시 목록을 재조회한다. 권한/비활성화/숨김/현재 동의와 하트 자격은 SQL에서 매번 확인하여 별도 집계 캐시의 무효화 경로를 만들지 않는다. 기존 cheer_likes 기본키 `(card_id,user_id)`와 새 `ix_content_public_feed(kind,visibility_status,archived_at,id)`를 사용한다. 최신순은 하트 집계를 생략하고 하트순은 유효 하트를 카드별로 묶어 집계한다. 하트 수는 런타임 집계이므로 해당 값의 정렬 자체가 B-tree 인덱스 정렬이라는 의미는 아니다.

FAST TAP V20과 독립적으로 적용되며 버전 충돌/병합 순서 의존을 만들지 않도록 `R__cheer_palette_and_feed_index.sql` repeatable migration을 추가했다. 불변 revision을 UPDATE하지 않고 CHECK 허용 팔레트와 인덱스만 선언한다. 인덱스가 이미 있으면 다시 생성하지 않아 재실행할 수 있다.

로컬 29개 합성 카드의 EXPLAIN ANALYZE에서 최신순은 PK 역방향 스캔과 LIMIT, 하트순은 유효 하트 집계 후 정렬을 확인했다. 작은 표에서는 옵티마이저가 새 복합 인덱스 대신 PK/표 스캔을 선택할 수 있으며 강제로 인덱스를 지정하지 않는다. 실제 행사 부하나 인덱스 전후 속도 향상을 측정했다는 의미는 아니다.
