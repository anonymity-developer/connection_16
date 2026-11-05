# 소원 댓글과 응원 하트 — 페이즈 4-3

[콘텐츠 계약](content.md)의 소원/응원과 검수 작업을 확장한다. 실제 DDL은 새 V7이고 기존 V1~V6은 변경하지 않는다. 정확한 DTO는 [반응 OpenAPI](reactions.openapi.yaml), 코드 시작점은 ReactionController/ReactionService/ReactionRepository다.

## 현재 범위

- 승인된 소원의 댓글 작성/공개 목록/본인 접수 내역과 승인된 응원 카드 하트 등록·취소·상태 조회를 구현한다.
- 댓글은 불변 revision과 기존 검수 작업/관리자 판정에 연결한다. 검수 제공자 미설정 기본값은 계속 REVIEW_REQUIRED다. 실제 외부 업체 호출이나 운영 데이터 생성은 수행하지 않는다.
- 댓글과 하트로 미션 글자/완료 순번/수상 결과를 추가 생성하지 않는다. 댓글 활동상과 하트 반응상의 정산·확정은 후속 구현이다.
- 댓글 수정/삭제/신고, 프로필 상세 링크와 동료 탐색, 관리자 운영 화면·숨김 API는 이번 범위가 아니다. 거절 댓글을 고쳐 남기려면 새 댓글을 제출하며 이전 접수 내역은 유지한다.

## 접근과 기간

공개 댓글/하트 읽기도 로그인 세션이 필요하다. 쓰기는 추가로 CSRF, 행사 OPEN, 작성자의 유효한 현재 프로필/공개 동의와 해당 미션 해금을 확인한다. 댓글은 MAKE_A_WISH, 하트는 SAY_SOMETHING 해금이 필요하다. 다른 사람의 승인된 글을 읽는 데 해금은 필요하지 않다.

부모 글은 종류(WISH/CHEER), 승인 포인터와 APPROVED, 비숨김, 작성자 ACTIVE/공개 프로필 및 현재 동의 두 개를 함께 확인한다. 없거나 현재 비공개인 대상은 같은 404로 응답한다. 하트 취소도 시상에 영향을 주는 쓰기이므로 제출 마감 뒤에는 차단한다. 본인 댓글 접수 이력은 원본 소원이 비공개가 되어도 읽을 수 있지만 소원 본문이나 타인의 정보는 반환하지 않는다.

## 댓글 저장과 재시도

POST `/wishes/{wishId}/comments`는 `{content, revealProfile}`과 Idempotency-Key를 받는다. content는 공백 제외 1~200 UTF-16 code unit이고 앞뒤 공백만 제거한다. 기본 UI는 revealProfile=false다. true인 승인 댓글에는 현재 공개 닉네임을 표시하고, false면 nickname=null이다. 현재 공개 DTO에는 true여도 작성자 ID/프로필 링크를 넣지 않는다. 실제 프로필 연결은 탐색 단계에서 별도 구현한다.

키는 16~64자의 영문/숫자/`_`/`-`다. `(author_id,idempotency_key)` UNIQUE와 wish ID/본문/표시 설정 해시를 비교한다. 같은 키/같은 요청은 같은 댓글의 현재 상태, 다른 부모/내용/표시는 409다. 키를 새로 발급하면 별개의 댓글이다. 같은 소원에 여러 댓글을 남길 수 있으며 미션의 공식 소원 슬롯과 구분한다. 작성은 사용자당 30회/분으로 제한한다.

한 트랜잭션에서 COMMENT item, 불변 revision, 부모/작성자 bridge, 검수 job, COMMENT_REVISION receipt를 저장하고 202를 반환한다. 댓글마다 revision 1개이며 공개 전에는 작성자와 관리자만 본문을 볼 수 있다. 댓글 접수로 mission_progress를 IN_PROGRESS로 바꾸지 않는다.

본인 상태는 id/content/revealProfile/status/reason/submittedAt/requestKey를 반환한다. requestKey는 응답 유실 복구용 본인 전용 식별자이며 공개 목록에 포함하지 않는다. 프론트는 응답을 확인하지 못하면 본문/키/표시 설정을 유지한 채 입력을 잠그고 같은 요청을 다시 보낸다. 본인 내역 새로고침에서 같은 requestKey가 확인되면 초안과 재시도 상태를 해제한다. 최근 20건에서 찾지 못해도 새 키로 자동 전송하지 않고 같은 요청 재시도를 유지한다. 새로고침 후 서버 내역을 읽으며 본문/키를 브라우저 영구 저장소에 보관하지 않는다.

공개 목록과 본인 내역은 댓글 ID 내림차순으로 20개, 다음 cursor로 이어진다. 댓글 ID는 공개 콘텐츠 식별자이며 작성자 ID가 아니다. 하트 참여자 목록이나 익명 작성자를 역추적하는 경로는 없다.

## 검수와 공개 상태 경합

lease/fence/재시도/외부 호출 경계는 [콘텐츠 계약](content.md)을 그대로 따른다. 자동·관리자 승인 시 댓글 작성자와 소원 작성자의 ID를 먼저 오름차순으로 잠근다. 관리자 판정은 관리자 ID도 함께 정렬한다. 이후 item/job/fence/pending/lease와 부모의 현재 공개 조건을 확인한다.

대기 중 원본 소원이 숨겨지거나 작성자/동의/미션 조건이 바뀌면 승인을 강행하지 않고 REVIEW_REQUIRED로 남긴다. 제출 마감 뒤에도 검수 마감까지, 제출 기간 내에 접수한 댓글만 승인할 수 있다. 댓글 승인은 공개 포인터와 이력만 저장하고 MissionCompletionService를 호출하지 않는다. 댓글 거절이 소원/응원의 mission_progress를 초기화하지 않도록 기존 거절 경로도 종류를 제한한다.

공개 댓글 SQL은 부모와 댓글 작성자의 현재 공개 조건을 하나의 조회에서 함께 검사한다. 부모를 조회한 직후 비공개가 되더라도 댓글 목록 쿼리에서 다시 제외한다. 공개 철회와 대기 중 승인 경합은 실제 MySQL 사용자 잠금으로 직렬화한다. 공개 조회는 해당 쿼리의 일관된 시점이며 이미 전달한 응답을 회수하는 기능은 아니다.

## 하트의 서버 상태

GET/PUT `/cheers/{id}/like`는 `{liked,count,canLike}`를 반환한다. DELETE는 204이며 프론트는 GET으로 상태를 확인한다. PUT은 항상 등록, DELETE는 항상 취소다. toggle API가 아니므로 같은 요청을 여러 번 보내도 반대로 뒤집히지 않는다.

`cheer_likes`의 `(card_id,user_id)` PK가 중복을 막는다. 자신의 카드는 읽을 수 있지만 등록/취소는 403이고 canLike=false다. 실제 쓰기는 사용자/카드 작성자 ID를 오름차순으로 잠근 뒤 현재 권한을 다시 검사한다. 클라이언트의 count/canLike/작성자 값은 인가 근거로 사용하지 않는다. 쓰기 속도 제한은 사용자당 60회/분이다.

count는 현재 ACTIVE/공개 프로필/현재 두 동의가 유효한 참여자의 서로 다른 하트 수다. 개인정보 공개를 철회한 참여자의 행을 즉시 삭제하지 않더라도 공개 집계에서 제외한다. 이것은 잠정 공개 집계이며 수상 확정 점수가 아니다. liked는 본인의 저장 행, canLike는 현재 본인이 등록/취소할 수 있는 조건이다.

GET `/cheers/likes?ids=...`는 서로 다른 양수 카드 ID 최대 20개를 받아 ID → LikeState 맵을 반환한다. 현재 공개되지 않은 ID/다른 종류는 동일하게 생략한다. 목록은 한 번의 HTTP 요청과 한 번의 상태 SQL로 묶어 조회한다. 작성자 ID/하트 참여자 ID를 응답에 포함하지 않는다. 페이지를 더 불러오면 새 카드만 묶음 조회한다. 낱개 GET은 복구/상태 재확인에 사용한다.

화면은 숫자를 미리 증감하지 않는다. PUT/DELETE 응답이 유실되면 같은 의도를 재시도하거나 서버 상태로 복구한다. 등록 후 모르는 응답 때문에 취소를 보내는 자동 toggle은 없다. 묶음 조회 실패 시 0으로 대체하지 않고 조회 오류와 재확인 버튼을 표시한다. 서버가 거절한 변경은 상태를 다시 읽기 전까지 막는다.

## V7 데이터 구조

| 구조 | 실제 제약 |
| --- | --- |
| content_items 확장 | kind에 COMMENT 추가. slot_kind generated column은 WISH/CHEER일 때만 kind, 나머지는 NULL. UNIQUE(author_id,slot_kind)로 원래 1회 슬롯 유지/다중 댓글 허용. UNIQUE(id,kind)/INDEX(kind,id) 추가. 내 슬롯은 slot_kind로 직접 조회 |
| wish_comments | id PK, wish_id, content_item_id UNIQUE, author_id, idempotency_key/hash. UNIQUE(author_id,key), INDEX(wish_id,id), INDEX(author_id,wish_id,id) |
| 댓글 FK | `(wish_id,wish_kind)`는 WISH, `(content_item_id,item_kind)`는 COMMENT, `(content_item_id,author_id)`는 실제 작성자와 일치. kind 상수는 CHECK로 고정 |
| cheer_likes | (card_id,user_id) PK, created_at. 카드의 kind=CHEER 복합 FK와 사용자 FK |
| submission_receipts 확장 | COMMENT_REVISION을 새 CHECK 허용 목록에 추가. 기존 근거는 변경하지 않음 |

댓글 본문은 content_revisions, 검수 상태는 기존 job/review에 남는다. 작성자/부모 관계를 클라이언트가 선택하는 별도 userId/role 필드는 받지 않는다. 댓글 활동상 집계는 향후 유효 댓글의 `COUNT(DISTINCT wish_id)`를 기본으로 하되 자기 소원/반복 댓글/숨김·비공개 상태를 제외하는 별도 정산으로 구현해야 한다.

## 검증

실제 MySQL/Redis 통합 검사에서 동시 댓글 재전송, 원래 슬롯 UNIQUE 유지, 부모/댓글 작성자 공개 철회, 공개 철회와 승인 경합, 오래된 lease/중복 판정, 마감 뒤 검수, 저장 롤백, 중복 하트/취소, 자기 카드/잘못된 종류/미해금/비공개 차단, 집계 필터, 묶음 조회 제한, SQL FK와 CSRF/위조 필드를 확인한다.

브라우저는 명시적인 API 대역으로 댓글 지연 조회/빈 입력·초점/비공개 대기/동일 요청 재시도/요청 키 복구/HTML 이스케이프/닉네임 설정/부모 비공개와 하트 묶음 조회/중복 재시도/서버 거절/오류 시 숫자 미조작을 검사한다. 360/390/430/768/1440px 캡처를 확인하며 실제 참가자 데이터·외부 제공자·실기기/운영 부하 검증과 구분한다.
