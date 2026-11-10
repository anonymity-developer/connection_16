# 관리자 검수·공개 관리·감사 — 페이즈 5-2

`/admin`에서 검수 작업을 고르고 접수본과 승인본을 비교한 뒤 사유를 남겨 판정한다. 별도 콘텐츠 탭에서 숨김/복구를, 감사 탭에서 조치자·대상·사유·변경 메타데이터를 확인한다. 정확한 요청/응답은 [운영 OpenAPI](operations.openapi.yaml), 기존 검수/공개 포인터는 [콘텐츠 계약](content.md)을 따른다.

## 구현 범위

- 검수 작업의 상태 필터/오래된 순 커서 목록과 현재 작업 상세
- 사유가 필수인 기존 수동 승인/거절과 실제 판정 감사 기록
- REVIEW_REQUIRED인 현재 수정본의 제한된 자동 검수 재처리
- 소원/응원/댓글의 승인본·대기본과 운영자 숨김/복구
- 현재 페이지 건수, 실제 빈 상태/오류/충돌 복구와 모바일 목록·상세 전환
- 조치와 같은 트랜잭션에 저장하는 감사 메타데이터와 관리자 전용 조회

참가자 신고 접수/처리, 계정 제재·역할 변경 화면, 행사 설정/태그 발급 화면, 게임 재도전·순위 검토, 시상 정산과 개인정보 보관 기간 확정은 후속이다. 이번 V8에 content_reports나 시상 테이블을 넣지 않는다. 실제 외부 검수 제공자는 기존처럼 미설정이며 운영자·참가자·판정·행사 날짜를 시드하지 않는다.

## 관리자 권한과 데이터 경계

모든 경로에서 유효한 세션/자격증명 버전/ACTIVE 계정과 현재 DB의 ADMIN 역할을 확인한다. body나 오래된 세션의 역할만 믿지 않는다. 관리자 본인의 공개 프로필이나 미션 해금은 운영 권한의 조건이 아니다. 일반 계정은 대기 원문·승인본·작성자 내부 ID·감사 기록을 조회할 수 없다. 잘못된 대상 ID도 관리자 권한 확인 뒤 처리해 존재 여부를 공개하지 않는다.

`GET /admin/access`는 `{admin:true}`만 반환한다. 프론트는 이 확인 전 비공개 목록/상세를 요청하지 않는다. 권한 회수나 세션 만료 뒤 확인 실패 시 기존 운영 원문을 지운다. 조회는 no-store이며 원문/조치 사유/판정 상태를 브라우저 영구 저장소에 쓰지 않는다. 문서 제목과 URL에는 글 원문/사유/작성자 이름을 넣지 않는다.

관리자 지정 API/자동 승격/공용 운영자 비밀번호는 없다. 실제 운영 계정과 역할 부여는 별도 운영 절차로 확정해야 한다. 이번 작업은 운영 DB의 역할을 변경하지 않았다.

## 경로와 목록 계약

서버 접두사는 `/api/v1`이다. 모든 목록의 limit은 기본 20, 1~50이며 UI는 20을 요청한다. 응답은 `{items,nextCursor}`다. 현재 페이지 개수는 전체 대기 건수가 아니다.

| 경로 | 현재 계약 |
| --- | --- |
| GET `/admin/access` | 현재 ADMIN 접근 검사 |
| GET `/admin/moderation/tasks` | 상태 필터, ID 오름차순, `after` ID 초과. 기본 PENDING은 QUEUED/PROCESSING/REVIEW_REQUIRED |
| GET `/admin/moderation/tasks/{id}` | 현재 job/fence/revision/승인본, kind/visibility/version/수동 재처리 횟수 |
| POST `/admin/moderation/{id}/decision` | 기존 수동 판정, expectedFence/APPROVED 또는 REJECTED/reason |
| POST `/admin/moderation/{id}/retry` | expectedFence/reason, 같은 수정본의 제한 재처리 |
| GET `/admin/content` | kind/visibility 필터, ID 내림차순, `cursor` ID 미만 |
| GET `/admin/content/{id}` | 현재 승인본·대기본·최근본/공개 허용/version, 댓글의 원글 ID |
| PATCH `/admin/content/{id}/visibility` | expectedVersion/VISIBLE 또는 HIDDEN/reason |
| GET `/admin/audit-logs` | 조치 필터, ID 내림차순, `cursor` ID 미만 |

검수 상태는 PENDING/QUEUED/PROCESSING/REVIEW_REQUIRED/APPROVED/REJECTED/SUPERSEDED/ALL이다. 콘텐츠 종류는 WISH/CHEER/COMMENT, 공개 상태는 VISIBLE/HIDDEN이다. 감사 action은 MODERATION_DECISION/MODERATION_RETRY/CONTENT_VISIBILITY다. 빈 값의 필터는 생략하며 잘못된 값은 400이다. cursor/after는 양수이며 첫 요청은 생략한다. 새 작업이나 상태 변화로 이전 페이지에 처리 대상이 생길 수 있어 첫 페이지 재조회 경로를 제공한다.

기존 GET `/admin/moderation`의 최대 50건 배열 응답은 호환 유지한다. 새 화면은 커서가 있는 tasks 경로를 사용한다. 관리자 상세는 검수용 비공개 자원이며 공개 프로필 POST 경계를 우회하는 경로가 아니다. Mattermost ID/username, 자격증명/해시/원문 OTP·태그, revision payload_hash는 운영 DTO에도 넣지 않는다.

## 판정과 감사

수동 판정은 현재 pending revision과 expectedFence가 일치하고 QUEUED/PROCESSING/REVIEW_REQUIRED일 때만 가능하다. expectedFence는 누락할 수 없다. 검수 기간은 행사 개장부터 moderation_closes_at까지다. 수동 fence를 증가시켜 늦은 자동 응답을 무효화하고 기존 검수 로직으로 현재 참여 조건/부모 공개 상태를 다시 확인한다.

APPROVED 요청도 계정/동의/해금/기간/공개 상태가 맞지 않으면 실제 상태는 REVIEW_REQUIRED로 남는다. UI는 승인 요청 전송만으로 승인 완료나 글자를 표시하지 않는다. 저장 후 서버 상세를 다시 조회한다. moderation_reviews의 판정/사유/fence 이력과 별개로 admin_audit_logs에 요청 결정 및 실제 전후 상태를 함께 남긴다. 자동 검수 판정은 기존 moderation_reviews에 남으며 관리자 조치로 위장하지 않는다.

## 재처리 규칙

같은 현재 pending revision의 REVIEW_REQUIRED 작업만 재처리할 수 있다. expectedFence가 다르거나 교체/종료된 작업은 409 REVIEW_CHANGED다. 숨김은 409 CONTENT_HIDDEN, 참여 조건 미달은 409 PARTICIPATION_UNAVAILABLE, 검수 기간 밖은 403 MODERATION_CLOSED다. 댓글은 원글의 현재 공개 조건도 확인한다.

성공 시 기존 job을 QUEUED로 돌리고 attempts=0, lease=null, next_run_at=현재 DB UTC, fence+1, manual_retry_count+1로 바꾼다. revision 본문·번호·접수 시각/영수증·기존 승인 포인터·완료 근거를 새로 만들거나 덮어쓰지 않는다. 같은 job의 수동 재처리는 최대 3회이며 DB CHECK도 0~3을 강제한다. 자동 시도 최대 3회와 수동 재처리 횟수는 별개다. 한도 뒤에는 원문을 읽고 사유를 남기는 수동 판정을 사용한다.

외부 제공자 호출은 기존 worker가 DB 트랜잭션 밖에서 수행한다. retry API가 호출을 직접 실행하거나 트랜잭션 안에서 기다리지 않는다. 제공자 미설정/연락처·URL 검출이면 다시 REVIEW_REQUIRED일 수 있다.

## 숨김·복구와 동시성

공개 상태 변경은 현재 content_items.version과 expectedVersion을 비교한다. 변경됐으면 409 CONTENT_CHANGED, 같은 현재 상태 요청은 추가 감사 없이 성공하는 no-op이다. 실제 변경은 version+1이며 사유가 필요하다. 콘텐츠 공개 조치는 행사 마감 이후에도 허용해 사후 대응할 수 있다.

HIDDEN으로 바꾸면 현재 대기 job의 fence를 올리고 lease를 해제해 REVIEW_REQUIRED/CONTENT_HIDDEN으로 둔다. 이미 나간 자동 검수 응답은 반영하지 않는다. 복구는 승인된 글의 공개 허용만 바꾸며 미승인 본문을 자동 승인하거나 대기 검수를 자동 재개하지 않는다. 기존 승인 포인터/미션 글자/완료 근거/접수 시각/하트·댓글 원장은 삭제하지 않는다. 원글 숨김은 공개 댓글 조회/반응에서도 부모 조건으로 적용한다.

실제 노출은 VISIBLE만으로 확정되지 않는다. 승인본/ACTIVE 계정/프로필 공개/현재 동의와 댓글 부모 조건을 기존 공개 API가 다시 확인한다. 운영자가 VISIBLE로 복구해도 참가자의 공개 철회나 계정 제한을 우회하지 않는다.

여러 사용자가 필요한 조치는 관리자/작성자/댓글 원글 작성자 ID를 중복 제거해 오름차순으로 먼저 잠근다. 그 뒤 콘텐츠 → job → 완료 근거/전역 순번 순서를 따른다. 조치와 감사 기록은 같은 트랜잭션이다. 감사 저장 실패면 숨김/재처리/판정·포인터·글자 지급도 모두 롤백한다. 서비스 쓰기 제한은 재처리/공개 조치 각각이 공유하는 관리자당 60회/분이다.

## 감사 데이터와 보관

V8 admin_audit_logs는 actor_id, action, target_type/id, reason, before_json/after_json, created_at을 저장한다. before/after에는 item ID, 공개 상태, 콘텐츠 버전, 승인/대기 revision ID, job 상태/fence, 재처리 횟수와 요청 결정 등 필요한 메타데이터만 둔다. 본문/비밀번호/토큰/외부 응답은 복제하지 않는다. 사유는 1~300자이며 운영자가 불필요한 개인정보나 비밀값을 적지 않도록 안내한다.

감사 조회는 ADMIN 전용이며 편집/삭제 API는 없다. 애플리케이션 경로가 추가 기록만 한다는 뜻이며, DB 권한 분리/외부 보관/WORM·암호학적 변조 방지를 구현한 것으로 주장하지 않는다. 보관 기간/백업의 개인정보 처리와 역할 부여는 운영 확정 대상이다.

## 화면과 실패 복구

데스크톱은 작업 목록과 원문 비교 영역을 함께, 900px 이하에서는 목록 또는 선택한 상세 한 영역만 표시한다. URL에는 탭/필터/커서/선택 ID만 보존해 뒤로가기·새로고침을 지원한다. 사유는 URL에 넣지 않는다. 현재 페이지가 아닌 대상도 관리자 상세로 재조회할 수 있다.

조치 전 확인창에 종류/대상 ID/수정본과 영향을 표시한다. 빈 사유는 첫 오류 필드로 이동하며, 전송 중 중복 요청·닫기/취소를 막는다. 409나 응답 유실 뒤에는 같은 값으로 자동 재전송하지 않고 추가 조치를 차단한다. “서버 상태 다시 확인”은 GET으로 현재 fence/version/판정을 재조회한 뒤 새 조치를 고르게 한다. 다른 탭으로 이동한 뒤 늦게 돌아오는 응답은 버린다.

## 검증과 남은 운영 확인

실제 MySQL/Redis 통합 테스트로 현재 역할/인가·CSRF, 커서/공개·대기 분리, 숨김과 늦은 worker/원글 댓글, 동시 버전 변경, 재처리 제한/원접수 보존, 감사 실패 롤백과 실제 판정 기록을 검사한다. 기존 콘텐츠·댓글 테스트도 V8의 감사 FK 순서에 맞춰 테스트 데이터만 정리한다.

브라우저 계약 대역으로 익명/일반 계정의 원문 요청 차단, 상태/페이지 이동, 5개 폭, 승인본 비교/HTML 이스케이프, 사유·busy, 충돌/응답 유실 후 GET 복구, 재처리 한도, 숨김·복구/감사, 역할 회수/늦은 응답을 검사한다. 렌더 검사에는 같은 임시 loopback 서버의 읽기 전용 대역만 제공하며 앱/운영 설정에 인증 우회를 넣지 않는다. 실제 운영자/참가자/판정·DM·외부 검수·배포·휴대전화/현장 부하 검증과 구분한다.

## 시상 후보 조회 후속

최신 미션 통합 패치 위의 시상 후속은 GET /api/v1/admin/awards와 /admin?tab=awards를 제공한다. 기존 현재 DB ADMIN/credential/no-store를 재사용하고 한 REPEATABLE_READ 안에서 계산한다. 새 테이블·확정/지급/추첨·감사 쓰기는 없다. 위의 과거 ‘시상 후속’ 표현 중 **잠정 후보 조회**만 구현되었으며 확정·정정·배정·영구 snapshot은 여전히 후속이다. [집계 기준](awards-candidates/RULES.md), [검증 한계](awards-candidates/TEST_RESULTS.md)를 따른다.

## 사후 운영 — 조회 전용

`/admin?tab=results`: 같은 DB 스냅샷의 집계·기존 잠정 시상 후보·자료 지문 비교와 CSV/JSON/Markdown. 상세 기준/권한/한계는 [post-event/API.md](post-event/API.md), 개인별 로그 비활성 설계는 [정의서](post-event/DATA_DICTIONARY.md). 공식 확정·추첨·삭제·새 행동 추적 없음.
