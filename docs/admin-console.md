# 관리자 콘솔 — 페이즈 5-3

`main`의 콘텐츠 검수 기능에 독립 관리자 로그인, 참가자/접속 분석, 미션별 순위와 관리자 전용 미리보기를 추가한다. 최종 시상 정산은 후속이다.

## 계정 등록과 로그인

서버 환경변수에 `ADMIN_BOOTSTRAP_ID=admin:tagme`, `ADMIN_BOOTSTRAP_PASSWORD`(운영자가 정한 15~64자), `AUTH_HMAC_KEY`(32바이트 이상 난수의 Base64)를 설정한다. 기본/공용 비밀번호는 없다. 아이디는 `admin:` 뒤 소문자 영문/숫자/`.`/`_`/`-` 1~48자다. `:`는 참가자 Mattermost username 문법 밖이라 두 계정 영역이 충돌하지 않는다.

서버 시작 시 `local_admin_accounts`와 `user_credentials`에 계정과 해시를 등록한다. 실제 Mattermost 계정/참가자 프로필은 만들지 않는다. 두 bootstrap 값을 모두 비우면 새 계정을 만들지 않고 기존 계정은 유지한다. 같은 아이디에 다른 비밀번호를 설정하고 재시작하면 해시/credential version을 갱신해 기존 세션을 차단한다. 정지·권한 회수된 운영자를 bootstrap이 자동 복원하지 않는다. 비밀번호는 서버 환경변수에만 보관하며 Git·로그·브라우저 설정에 넣지 않는다.

기존 `/auth`의 동일한 아이디/비밀번호 폼에서 로그인한다. `POST /api/v1/auth/login`이 `admin:` 계정만 별도로 검증하며 Mattermost API·DM은 호출하지 않는다. 참가자 인증은 기존 경로를 유지한다. `AUTH_ENABLED=false`여도 관리자 로그인은 가능하고 HMAC 기반 시도 제한, CSRF, 세션 ID 교체, Redis 세션과 현재 DB 역할/credential version 검사를 적용한다.

다른 브랜치의 로컬 스택과 함께 실행할 때는 `.env`의 `COMPOSE_PROJECT_NAME`과 `WEB_PORT`/`API_PORT`/`MYSQL_PORT`/`REDIS_PORT`를 별도로 정한다. 해당 작업 공간에서 `docker compose -f compose.yml -f compose.dev.yml --profile app up -d --build --wait`로 실행한다. 관리자만 확인하려면 Mattermost 설정은 필요하지 않다.

사용자 응답에 `admin`이 추가되어 관리자 로그인은 `/admin`으로 이동한다. UI 플래그 자체가 권한은 아니다. `/auth/config`의 기존 필드 외에 `participantAvailable`, `adminAvailable`이 추가된다. `available`은 둘 중 하나가 준비됐다는 뜻이고 DM 요청은 `participantAvailable`을 사용한다.

## 화면과 API

요청·응답 스키마는 [관리자 OpenAPI](admin.openapi.yaml)를 따른다. 로그인 응답은 기존 [인증 OpenAPI](auth.openapi.yaml)에 포함한다.

아래 API는 `/api/v1` 접두사와 유효한 서버 ADMIN이 필요하고 no-store다. 인증/비밀번호/외부 ID는 응답하지 않는다.

| 화면 | 읽기 API | 내용 |
| --- | --- | --- |
| `/admin` | `/admin/overview` | 가입/유효 계정/전체 완료/접속 세션 수, 다섯 미션 해금·참여·완료 |
| `/admin/members` | `/admin/members?offset=0&limit=25&q=...` | 가입 순서, 이름/닉네임/아이디/지역·반, 시각, 미션 수, 접속 시간 |
| `/admin/members/{id}` | `/admin/members/{id}` | 상세 프로필·관심사·링크, 미션별 결과와 최근 접속 30건 |
| `/admin/rankings` | `/admin/rankings/{mission}?offset=0&limit=25` | 미션별 현재 유효 순위와 계산 기준 |
| `/admin/completions` | `/admin/completions?offset=0&limit=25` | 기존 운영상 전체 완료 순서 |
| `/admin/preview` | `/admin/preview/sync` | 미리보기용 문항 정의와 EXAMPLE/CONFIGURED 구분 |
| `/admin/moderation` | 기존 검수/콘텐츠/감사 API | 기존 승인·거절·재처리·숨김·복구·감사 |

목록은 offset 0~100,000, limit 1~50(기본 25), `{items,total,offset,nextOffset}`를 사용한다. 검색은 최대 64자로 이름/닉네임/아이디에 적용하고 `%`, `_`, `!`를 문자로 취급한다. 가입 순서는 검색/페이지와 무관하게 `created_at → id`로 계산한다. 제한·탈퇴 계정도 목록에 상태를 남긴다. 독립 운영자는 참가 통계에서 제외하고, 가입 자격증명이 없는 더미/레거시 프로필은 가입자 수에 포함하지 않는다.

해금은 progress 존재, SHOW ME 참여는 완료, SYNC는 결과 존재, TAP은 공식 슬롯 존재, 소원/응원은 작성 슬롯 존재로 구별한다. 여러 집계/목록을 한 응답에 조합하는 DB 조회는 같은 트랜잭션에서 수행한다.

## 순위

| 미션 | 기준 | 동점 |
| --- | --- | --- |
| SHOW ME·SYNC ME | 완료 시각 오름차순 | 회원 ID |
| FAST TAP | 현재 슬롯의 VALID 공식 탭 수 내림차순 | 제출 시각, 회원 ID |
| MAKE A WISH | 받은 유효 승인 댓글 수 내림차순 | 완료 시각, 회원 ID |
| SAY SOMETHING | 받은 유효 하트 수 내림차순 | 완료 시각, 회원 ID |

해당 미션을 완료한 현재 ACTIVE·가져오기 완료·공개 프로필·두 정책 동의 계정만 포함한다. 소원/응원은 공개 가능한 승인본이 필요하다. 숨김·보류 글, 비유효 게임, 자기 댓글/하트, 비적격 반응 사용자는 제외한다. 소원 지표는 받은 댓글 수이며 댓글 활동상 후보 집계와 다르다.

현재 기록의 관리자 확인용 순위다. 시상 snapshot/중복 수상/최종 확정은 하지 않는다. 전체 완료 화면은 기존 `completion_order`이며 검수 지연을 보정하는 유효 원접수 기반 수상 순위로 표시하지 않는다.

## 접속 시간

V10 `connection_sessions`는 로그인마다 임의 UUID, 사용자/credential version, 시작·최근 요청·유휴 만료·명시적 종료 시각과 종료 사유를 저장한다. 브라우저 쿠키/Redis 세션 ID는 저장하지 않는다. 인증된 요청은 만료 예정 시각을 갱신하고 로그아웃·재로그인·비밀번호 변경·권한 회수는 기존 기록을 종료한다.

시간은 시작부터 명시적 종료까지, 미종료라면 `min(현재, 유휴 만료)`까지다. Redis 만료 이벤트가 누락되어도 무한 증가하지 않도록 조회 때 만료 상한을 적용한다. main 기본 유휴 만료는 2시간이다.

- 사용자가 선택한 로그인~로그아웃/세션 만료 시간이며 실제 화면 집중 시간은 아니다.
- 브라우저를 닫아도 로그아웃하지 않으면 유휴 만료까지 포함한다.
- 동시 기기는 각각 합산하고 현재 접속 수도 유효 세션 수다.
- 도입 이전 기록은 소급하지 않는다. 없으면 `기록 없음`, 측정된 0은 `0시간 0분 0초`다.
- 원장 보관/삭제 기간은 실제 개인정보 정책에 맞춰 별도로 확정해야 하며 현재 자동 삭제하지 않는다.

## 관리자 전용 미션 미리보기

서버 `/admin/access` 승인 뒤에만 마운트하고 포커스 복귀와 30초마다 권한을 재확인한다. 실패하면 화면을 제거한다. 참가자의 기존 태그/정책/기간/1회 제한 API를 면제하지 않는다.

기존 프로필·SYNC·FAST TAP·소원·응원 컴포넌트를 재사용하되 미리보기에서만 별도 React context의 메모리 클라이언트를 제공한다. 이 클라이언트는 `request`/`fetch`를 호출하지 않는다. 실제 계정·프로필·동의·해금·완료·콘텐츠·성향·게임 기록을 바꾸지 않으며 화면 이탈/초기화 시 메모리 상태를 버려 반복 체험한다. 콘텐츠의 대기·승인·거절도 미리보기 안에서 전환한다.

성향은 등록 문항을 쓰고 미등록 상태에서는 `resources/preview/sync-demo.json`의 기존 초안을 **관리자 미리보기 예제**로 표시한다. 예제를 DB의 현재 운영 문항으로 등록하지 않는다. 외부 DM/검수 제공자도 호출하지 않는다.

## 검증

- `AdminConsoleIntegrationTest`: Mattermost 비활성 상태의 독립 로그인·CSRF·세션·권한 회수, 가입 순서/검색/페이지, 유효 순위/자기·숨김·비적격 반응 제외, 만료 상한·기록 없음, 미리보기 ADMIN 경계/공식 테이블 무변경.
- `npm run test:admin-ui`: 기존 로그인에서 관리자 이동, 회원/상세/순위/완료의 360/390/430/768/1440px, 검색, 다섯 미션 체험과 반복 초기화, 로그인 외 POST 없음, 권한 회수 시 제거. 합성 API 대역이다.
- 실제 로컬 앱의 등록 관리자 로그인/집계, TAP 연습·SYNC 예제도 확인한다. 새 DB의 참가자 0명은 실제 값 그대로 표시한다.
- 화면 방향: framed-sheet, seed 576897122. 기존 DM Sans/TAG ME 색상, 제품 고유 요소는 다섯 미션의 참여 막대다. 인증 후 운영 DOM의 Chrome 측정과 실제 브라우저 동작을 함께 검사한다.

실제 Mattermost·실기기 NFC/QR·운영 배포/현장 부하·최종 시상은 이 작업의 검증 범위가 아니다.
