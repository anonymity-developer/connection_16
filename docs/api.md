# API 개요

현재 API는 `/api/v1` 아래의 동일 출처 JSON API다. 브라우저 인증은 `TAGME_SESSION` 쿠키를 사용하고, 상태 변경 요청은 서버가 발급한 CSRF 토큰과 헤더를 요구한다. 시각은 UTC ISO-8601이며 사용자별·관리자 응답은 기본적으로 `no-store`다.

요청·응답 스키마는 도메인별 OpenAPI를 확인한다. 오류 신고·개별 계정/글자 조정은 아직 OpenAPI에 포함되지 않아 [지원 계약](support-and-member-controls.md)의 HTTP 표와 구현을 함께 확인한다.

| 영역 | 주요 경로 | 계약 |
| --- | --- | --- |
| 공개 기반 | `/meta`, `/csrf` | 이 문서 |
| 인증 | `/auth/**` | [auth.openapi.yaml](auth.openapi.yaml) |
| 프로필·참여 | `/users/me/profile`, `/event`, `/policies`, `/interests`, `/missions/**`, `/tags/**` | [participation.openapi.yaml](participation.openapi.yaml) |
| SYNC·FAST TAP | `/sync/**`, `/tap/**` | [results.openapi.yaml](results.openapi.yaml) |
| 소원·응원 | `/wishes/**`, `/cheers/**` | [content.openapi.yaml](content.openapi.yaml) |
| 댓글·하트 | 소원 댓글, 응원 하트 하위 경로 | [reactions.openapi.yaml](reactions.openapi.yaml) |
| 동료 탐색 | `/discovery/**` | [discovery.openapi.yaml](discovery.openapi.yaml) |
| 오류 신고 | `/error-reports`, `/admin/error-reports` | [지원 계약](support-and-member-controls.md) |
| 관리자 | `/admin/**` | [admin.openapi.yaml](admin.openapi.yaml), [operations.openapi.yaml](operations.openapi.yaml) |
| SYNC 퍼널 | `/sync/funnel-events`, `/admin/funnel` | [funnel.openapi.yaml](funnel.openapi.yaml), [집계 의미](funnel-analytics.md) |

`GET /meta`는 서비스 식별값과 다섯 미션 메타데이터를 반환하는 공개 경로이며 health/readiness 판정이 아니다. `GET /csrf`의 `{headerName, token}`을 받아 쓰기에 사용하고 로그인 등 세션 교체 뒤에는 다시 발급받는다. 토큰을 URL이나 로그에 넣지 않는다.

## 공통 규칙

- 인증이 필요한 요청은 현재 세션의 사용자만 신뢰한다. body/query의 사용자 ID로 주체를 바꾸지 않는다.
- 관리자 경로는 세션의 표시 값뿐 아니라 현재 DB의 `ADMIN` 역할과 credential version을 다시 확인한다.
- 쓰기는 CSRF, 행사 상태, 계정·프로필 자격, 미션 해금, 소유권과 reset generation을 도메인에 맞게 확인한다.
- 생성/제출은 `Idempotency-Key`와 DB 고유 제약을 함께 사용한다. 같은 키와 다른 payload는 `409`다.
- 목록 cursor는 응답의 `nextCursor`를 그대로 전달한다. 소원/동료는 숫자 기반, 응원은 정렬·방향·상한 ID를 묶은 문자열 커서다. 정렬/필터 변경 시 첫 페이지부터 읽는다.
- 원본 NFC 토큰은 태그 처리 요청에만 사용하고 응답·로그·DB 원문으로 남기지 않는다.
- 익명 콘텐츠 응답은 작성자 ID, 프로필 ID, 내부 점수와 추천 근거를 제거한다.

## 오류 형식

```json
{
  "code": "UNAUTHENTICATED",
  "message": "로그인이 필요합니다."
}
```

기본 오류는 `code`, `message`, `retryAfter`를 반환하며 요청 제한에는 `Retry-After` 헤더가 붙을 수 있다. Spring Security 필터의 인증/CSRF 오류도 처리해야 한다. version/generation 충돌 후에는 해당 상태 API를 다시 조회한다. 공통 `fieldErrors`나 `requestId` 응답 필드가 있다고 가정하지 않는다. 대표 상태는 다음과 같다.

| 상태 | 의미 |
| --- | --- |
| `400` | 형식·필드·커서·상태 전이 오류 |
| `401` | 로그인 필요 또는 만료 세션 |
| `403` | 현재 역할·참여 자격 부족 |
| `404` | 없거나 현재 사용자에게 공개할 수 없는 대상 |
| `409` | 중복 키/payload, version·generation·상태 충돌 |
| `429` | 계정/IP/행위 요청 제한 |
| `503` | 외부 인증·필수 운영 설정·의존성 준비 실패 |

## 보안 관련 특수 경로

- `POST /tags/inspect`: 로그인 전에도 활성 태그의 미션 이름만 확인한다. 해금·완료·임시 의도 소비는 하지 않는다.
- 태그 resolve/resume: 인증 후 서버가 토큰 해시와 사용자 상태를 확인해 해당 미션만 해금한다.
- 프로필 사진: 본인 또는 이미 연 상세의 공개 자격을 다시 확인하고 `no-store` PNG를 반환한다. 외부 Mattermost URL/ID는 노출하지 않는다.
- 관리자 지원 조치: CSRF, `Idempotency-Key`, expected generation, 사유와 명시적 확인을 요구하고 감사 이력을 같은 트랜잭션에 남긴다.

## 운영 결과

관리자 결과·시상 API는 현재 DB의 읽기 스냅샷으로 잠정 지표와 근거를 반환한다. 최종 수상 확정, 추첨, 지급, 개인정보 반출을 수행하지 않는다. 자료 지문이 바뀌면 오래된 내보내기 요청을 `409`로 거절한다. 자세한 경계는 [행사 종료 API](post-event/API.md)와 [운영 체크리스트](post-event/OPERATIONS_CHECKLIST.md)를 따른다.

사용자 동선은 [유저 플로우](user-flows.md), 서버·저장소 책임은 [아키텍처](architecture.md), 테이블 관계는 [ERD](erd.md)를 참고한다.
