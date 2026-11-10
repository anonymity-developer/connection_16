# 개발 및 운영 가이드

관리자만 먼저 실행하려면 `ADMIN_BOOTSTRAP_ID`, `ADMIN_BOOTSTRAP_PASSWORD`, `AUTH_HMAC_KEY`를 설정하고 `AUTH_ENABLED=false`를 유지할 수 있다. 등록 규칙·같은 로그인 폼·비밀번호 교체·접속 집계는 [관리자 콘솔](admin-console.md)을 따른다. Compose는 새 관리자 환경변수도 API에 전달한다.

## 1. 버전과 필수 도구

- JDK 21 (javac 포함; JRE만으로 컴파일 불가), Gradle Wrapper 8.14.3 (별도 Gradle 설치 불필요)
- Node.js 24, npm 및 `package-lock.json`
- Docker Engine 또는 Docker Desktop, Compose v2
- Spring Boot 3.5.16, MySQL 8.4, Redis 7.4, Caddy 2

Gradle 배포 파일은 SHA-256으로 검증하며 Wrapper JAR도 저장소에 포함한다. npm은 `npm ci`로 lockfile을 사용한다. Docker 이미지는 계열 태그를 사용하므로 운영 이미지 확정 시 테스트한 digest와 커밋 SHA를 함께 기록한다.

## 2. 환경변수

루트 `.env.example`을 `.env`로 복사하고 DB 비밀번호를 바꾼다. `.env`는 Git과 Docker build context에서 제외한다. 비밀번호에 공백/특수문자/달러 기호가 있다면 Compose dotenv의 따옴표 규칙을 확인하고, 셸의 `source .env`로 임의 실행하지 않는다.

| 변수 | 사용 위치 | 설명 |
| --- | --- | --- |
| DB_NAME / DB_USER / DB_PASSWORD | MySQL / API | 같은 값 사용; IDE 실행에도 지정 |
| DB_ROOT_PASSWORD | MySQL 초기화 | root 비밀번호, API에 전달하지 않음 |
| MYSQL_PORT / REDIS_PORT | 개발 포트 및 IDE API | 기본 3306 / 6379; 컨테이너 내부 포트는 고정 |
| API_PORT / WEB_PORT | 개발 Compose | 기본 8080 / 8088 |
| DB_HOST / REDIS_HOST | IDE API 또는 Compose | IDE는 localhost, Docker는 서비스명 |
| APP_DOMAIN | 운영 Caddy | 실제 DNS 이름; example.com은 예시 |
| APP_SESSION_SECURE | API | Compose 운영 override에서 true |
| AUTH_ENABLED | API | 기본 false; 실제 외부 권한 확인 후 true |
| MATTERMOST_BASE_URL / MATTERMOST_TOKEN / MATTERMOST_CHANNEL_ID | API 서버 전용 | 지정 채널 조회·DM 생성/작성 권한 필요. 브라우저/VITE 변수 금지 |
| AUTH_HMAC_KEY | API 서버 전용 | 최소 32바이트 무작위 Base64. 로컬에서 `openssl rand -base64 32`로 생성하고 .env에만 보관 |
| AUTH_OTP_* / AUTH_RESEND_SECONDS / AUTH_LOGIN_ACCOUNT_LIMIT / AUTH_IP_PER_MINUTE | API | `.env.example`의 기본 제한. 캠퍼스 공유 NAT와 실서버 비용 검증 후 조정 |
| AI_API_KEY | 후속 외부 연동 | 현재 사용 코드 없음; 브라우저/VITE 변수 금지 |
| API_PROXY_TARGET | 로컬 Vite | API 포트 변경 시 지정; 기본 http://127.0.0.1:8080 |

MySQL 볼륨이 이미 초기화된 뒤 `.env` 비밀번호만 변경해도 DB 계정의 실제 비밀번호는 바뀌지 않는다. 기존 DB에서 계정 비밀번호를 변경하고 API 설정을 맞춰야 한다. 볼륨 삭제는 데이터가 필요 없는 로컬 환경에서만 한다.

## 3. 로컬 구동

### 전체 컨테이너

```bash
docker compose -f compose.yml -f compose.dev.yml --profile app config --quiet
docker compose -f compose.yml -f compose.dev.yml --profile app up -d --build --wait
docker compose -f compose.yml -f compose.dev.yml --profile app ps
bash scripts/smoke.sh http://localhost:8088
```

소스 변경 후 전체 컨테이너 방식에서는 이미지를 다시 빌드해야 한다. HMR 개발은 IDE 방식을 사용한다.

### IDE 방식

```bash
docker compose -f compose.yml -f compose.dev.yml up -d --wait mysql redis
```

백엔드 실행 구성에 `DB_PASSWORD`를 설정하고 `TagmeApplication` 또는 `./gradlew bootRun`을 실행한다. 필요 시 DB_USER/DB_NAME/MYSQL_PORT/REDIS_PORT도 `.env`와 맞춘다. JDK는 21로 지정한다. IntelliJ/STS에서는 `backend`를 Gradle 프로젝트로 import한다.

프론트에서 `npm ci`, `npm run dev` 후 http://localhost:5173에 접속한다. 다른 API 포트를 쓰면 `API_PROXY_TARGET=http://127.0.0.1:8082 npm run dev`처럼 지정한다. PowerShell에서는 먼저 `$env:API_PROXY_TARGET='http://127.0.0.1:8082'`를 설정한다.

Vite의 loopback 바인딩은 로컬 개발용이다. 실기기 테스트 시 개발 서버 바인딩과 호스트 허용을 좁게 설정하거나 테스트 HTTPS 도메인을 사용한다. 일반 HTTP LAN 주소로는 운영 Secure 쿠키 및 NFC 전체 동작을 검증할 수 없다.

## 4. 테스트와 CI

프론트는 `npm run lint`, `npm run build`로 정적 검증한다. 초기 화면만을 위해 구현을 그대로 따라 쓰는 단위 테스트는 만들지 않는다. 사용자 상호작용이 추가되는 단계에서 Vitest/RTL과 Playwright를 도입한다.

백엔드는 `./gradlew test bootJar`, Docker가 있으면 `./gradlew integrationTest`를 실행한다. 단위 테스트는 공개 메타, 인증 차단, CSRF 토큰/차단을 확인한다. 통합 테스트는 실제 MySQL/Redis를 구동하고 Flyway, 5개 시드, 코드 UNIQUE, readiness를 확인한다. Docker가 없을 때 통합 테스트를 성공 처리하거나 조용히 skip하지 않는다.

CI는 pull request, main push 및 수동 실행으로 동작한다. 외부 Mattermost/AI 비밀키가 없어도 초기 검증은 가능하다. 서버 배포는 자동 실행하지 않는다.

2026-11-03 초기 구성 검증: 프론트 lint/프로덕션 빌드, 백엔드 단위 테스트 4개와 bootJar, 개발/운영 Compose config 및 셸 문법 검사를 통과했다. 작업 환경에는 Docker 데몬이 없어 Testcontainers와 전체 스택 smoke는 실행하지 못했다. CI 파일을 추가한 것과 CI 실행 성공은 구분한다.

## 5. 운영 구동

서버에 Docker/Compose, 저장소와 운영 `.env`를 준비하고 DNS 및 80/443 인바운드를 설정한다. 운영에서는 개발 override를 함께 사용하지 않는다.

```bash
docker compose -f compose.yml -f compose.prod.yml --profile app config --quiet
docker compose -f compose.yml -f compose.prod.yml --profile app up -d --build --wait
```

외부 공개 포트는 Caddy 80/443뿐이다. API/DB/Redis를 직접 공개하지 않는다. Caddy 인증서 볼륨과 DB 볼륨을 유지한다. 이 설정은 배포 준비 파일이며 실제 서버나 DNS가 연결된 상태가 아니다.

## 6. 로그와 장애 확인

```bash
docker compose --profile app ps
docker compose --profile app logs --tail=100 api
docker compose --profile app exec api curl -fsS http://localhost:8080/actuator/health/readiness
```

컨테이너 로그는 최대 파일 크기/개수를 제한한다. 비밀번호/해시, OTP, 세션, 외부 토큰, 이름/반/콘텐츠 원문을 로그에 출력하지 않는다. 디스크 여유 공간, API 오류율/지연, DB 커넥션과 잠금, Redis 메모리, 검수 대기 시간을 운영 관측 항목으로 삼는다.

후속 인증은 [인증 설계](auth.md)의 username/비밀번호 로그인과 가입/변경/재설정 DM OTP를 따른다. 기존 V1에는 username/비밀번호 해시가 없으므로 새 migration과 PasswordEncoder/세션 version 검사가 필요하다. 문서 변경만으로 로그인 기능이 추가된 것은 아니다. 비밀번호 정책/해시 알고리즘과 비용을 확정해 배포하고 기존 계정을 임의의 공용 비밀번호로 채우지 않는다.

Redis는 세션/OTP 유실이 경품 데이터 유실로 이어지지 않도록 사용한다. AOF를 활성화하고 noeviction 정책을 쓴다. 메모리가 가득 차면 쓰기가 실패할 수 있으므로 사용량과 실패를 확인한다. readiness 장애 시 UI/API가 무조건 정상이라고 안내하지 않는다.

## 7. 백업과 복원

```bash
bash infra/backup/backup.sh
```

루트 `backups`에 압축 SQL을 생성한다. 파일 권한을 제한하고 개인정보가 들어 있는 백업을 Git에 올리지 않는다. 운영에서는 서버 밖 저장소로 복사하고 보관/삭제 주기와 암호화를 설정한다. 스크립트 생성만으로 주기 실행이나 외부 보관이 설정되는 것은 아니다.

복원은 먼저 별도 테스트 DB/서버에 수행한다. 대상 환경을 확인한 후 아래 형태로 적용하고 Flyway 이력, 건수, API를 점검한다.

```bash
gzip -dc backups/선택한_백업.sql.gz | docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
```

데이터가 있는 DB에 복원하면 기존 테이블/데이터를 덮어쓸 수 있다. 운영 복구 전 쓰기 중단과 백업 파일/대상 확인 절차를 둔다.

## 8. 변경과 롤백

기능별 브랜치에서 작업하고 PR에 문제/변경/검증 결과를 적는다. 커밋은 `chore`, `docs`, `feat`, `fix`, `test` 등 목적을 드러내는 접두어를 사용한다. 브랜치 보호와 필수 검사 지정은 저장소 운영자가 별도 설정한다.

운영 배포 전 DB 백업 및 migration 호환성을 확인하고, 이전 이미지 태그를 보관한다. 애플리케이션 롤백은 이전 태그로 가능하지만 파괴적 DDL은 자동 복구되지 않는다. 데이터 변경은 새 migration 또는 검증한 복원 절차로 처리한다.

## 인증 구현 실행과 검증

기본 비활성 상태는 실제 DM을 발송하지 않는다. 서버에 토큰/채널/HMAC 키를 안전하게 제공하고 권한을 확인한 뒤 활성화한다. 로그/화면에 실제 키를 출력해서 점검하지 않는다. 외부 base URL은 HTTPS를 사용하고 HTTP는 로컬 테스트 서버만 허용한다. 쿠키/CSRF와 구체 흐름은 [인증 문서](auth.md)를 따른다.

```bash
cd backend
./gradlew test bootJar
./gradlew integrationTest
cd ../frontend
npm ci
npm run lint
npm run test:unit
npm run build
npx playwright install --with-deps chromium
npm run test:ui
npm run test:auth-ui
```

MySQL/Redis 통합 검사는 Docker가 필요하고 H2로 대체/생략하지 않는다. 브라우저 auth 검사는 테스트 파일에서 API를 가로채는 계약 검사이며 실제 DM 성공과 구분한다. CI는 두 브라우저 검사 결과와 인증 단계/계정 화면을 `frontend-ui-review` 아티팩트에 남긴다. 실제 외부 권한, 휴대전화, 현장 네트워크와 부하는 별도 검증한다.

## 페이즈 3 참여 준비

[참여 계약](participation.md)의 V4 설정/정책/태그 발급 절차를 따른다. 날짜나 검토되지 않은 동의 원문은 migration에 넣지 않는다. 운영자가 설정하기 전 NOT_CONFIGURED/정책 준비 상태가 정상이다.

- `node --test scripts/tag-bundle.test.mjs`: 32바이트/43자리/해시 일치와 저장소 밖 출력/덮어쓰기 거절.
- `cd frontend && npm run test:participation-ui`: 명시적 API 대역으로 프로필·정책·태그 복귀·수집판·철회·오류와 5개 폭 검사.
- `cd backend && ./gradlew test integrationTest bootJar`: 실제 MySQL/Redis에서 참여 동시성/롤백/인가와 기존 인증 회귀 검사.

브라우저 대역/로컬 발급 도구 성공은 운영 이벤트 개장이나 실제 NFC/DM 검증이 아니다. 태그 산출물은 개인 보관 디렉터리에만 만들고 원문을 Git/PR/분석/로그에 올리지 않는다. 실제 운영 DB 변경은 이 작업에서 수행하지 않았다.

## 페이즈 4-1 결과 미션

- `npm --prefix frontend run test:results-ui`: 명시적 API 대역으로 SYNC 5개 폭/결과 복원, 연습과 공식 분리, 같은 시작 key/종료 payload 재시도, 이탈 후 기회 소진을 검사한다.
- 서버 test/integrationTest는 실제 MySQL/Redis로 최초 결과/동시 시작·제출/프로필·해금/타인 소유/이벤트·결과 롤백을 확인한다. 시험용 DB 시각 대역은 운영 코드 우회가 아니다.
- 문항 초안 JSON은 서버 파서로 검증하지만 migration/프론트 fallback에 넣지 않는다. 운영자가 검토하고 [결과 계약](results.md)의 설정 절차로 별도 등록한다.
- 실제 기기의 연타/가려짐/확대 방지, 지연/중단과 시상/검토 정책은 별도 확인 대상이다. 공개 순위와 관리자 재도전은 아직 없다.

## 페이즈 4-2 콘텐츠

V6는 기존 마이그레이션 뒤에 적용한다. 검수 worker는 기본 실행되며 제공자가 미설정이면 REVIEW_REQUIRED로 보낸다. 설정/운 범위/관리자 판정의 실제 계약은 [콘텐츠 계약](content.md)과 [OpenAPI](content.openapi.yaml)를 따른다. 테스트는 processOne을 직접 호출하고 스케줄 worker를 꺼서 클래스별 컨테이너 종료 뒤 캐시된 컨텍스트가 재접속하지 않도록 한다. 운영의 worker 활성 기본값을 바꾸지는 않는다.

## 페이즈 4-3 데이터와 브라우저 검사

V7를 V6 뒤에 적용한다. npm run test:reactions-ui는 API 대역으로 댓글/하트와 5개 화면 폭을 검사한다. 실제 MySQL/Redis의 ReactionIntegrationTest는 동시 요청·공개 철회/승인 잠금 경합·FK·마감/롤백·집계를 검사한다. 실제 검수 업체/운영 데이터/배포와 구분한다.

CI의 Playwright/한글 폰트 설치는 Ubuntu HTTPS archive를 사용하고 APT idle timeout 30초/재시도 2회를 설정한다. 2026-11-05 Azure HTTP 미러의 다운로드 지연으로 작업이 취소된 로그를 근거로 바꿨다. 검사/폰트를 생략하거나 작업 시간 제한을 늘리지 않는다. 이 설정은 일회성 GitHub runner에만 적용한다.

## 페이즈 5-1 탐색/방문 검사

새 마이그레이션은 없다. 기존 V1 profile_views와 V4 현재 동의/행사 설정, V5 성향 결과를 사용한다. 운영 참여자나 방문을 시드하지 않는다. 정확한 실행 조건은 [탐색 계약](discovery.md)을 따른다.

- `npm --prefix frontend run test:discovery-ui`: 명시적 API 대역으로 익명/동의 게이트, 닉네임 필터/뒤로가기/늦은 페이지 응답, CSRF 상세 POST 1회, 새로고침/재시도/공개 철회/위험 URL, 실제 방향 관계/0과 공개 글 링크를 검사한다. 360/390/430/768/1440px 목록·상세·그래프 캡처와 discovery-contract-checks.json을 frontend-ui-review에 남긴다.
- `cd backend && ./gradlew test integrationTest bootJar`: 실제 MySQL/Redis에서 DiscoveryIntegrationTest의 동시 최초/반대 방문·최초 시각/철회 직렬화/롤백/현재 공개 필터/버전/상한/GET 우회/세션·CSRF를 검사한다. 기존 콘텐츠/댓글 테스트에는 승인된 익명 설정의 profileId 노출 조건도 포함한다.

로컬에는 JRE만 있고 Docker가 없어 백엔드 실행을 대신했다고 기록하지 않는다. 실제 백엔드/컨테이너와 브라우저 검증은 PR #8 CI에서 확인했다. 현재 경로 인증 완료 전 상세가 중복 마운트되는 회귀는 POST 횟수=1과 최초 방문 문구를 함께 검사한다.

## 페이즈 5-2 관리자 운영 검증

V8을 V7 뒤에 적용한다. 기존 migration은 변경하지 않으며 운영 계정/역할/실제 원문은 시드하지 않는다. admin_audit_logs와 manual_retry_count·운영 조회 인덱스가 추가된다. [운영 계약](operations-admin.md)과 [OpenAPI](operations.openapi.yaml)가 현재 기준이다.

- `npm --prefix frontend run test:operations-ui`: 테스트 전용 API 대역으로 ADMIN 게이트, 검수본 비교/HTML 안전 표시, 사유·busy/충돌·응답 유실 복구, 재처리 한도/공개 버전/감사/역할 회수와 늦은 응답을 검사한다. 목록·상세·콘텐츠·감사 5개 폭 캡처와 operations-contract-checks.json을 남긴다.
- 같은 임시 loopback preview의 읽기 전용 대역으로 운영 화면의 렌더 검사도 실행한다. 앱 코드나 운영 Vite 설정에 우회/가짜 운영자 상태를 넣지 않는다. 결과는 .taste-skill/operations-render와 CI frontend-ui-review에 남긴다.
- `cd backend && ./gradlew test integrationTest bootJar`: 실제 MySQL/Redis의 OperationsIntegrationTest에서 역할/인가/CSRF, 현재 버전·fence, 동시 변경, 재처리 제한/원접수, 숨김/worker·댓글, 감사 롤백/실제 판정을 검사한다.

현재 로컬에는 javac/Docker와 Playwright 브라우저 바이너리가 없어 해당 실행을 CI에서 확인한다. 로컬 lint/단위/빌드 성공을 실제 DB/브라우저 성공으로 기록하지 않는다. CI는 기존 검사/폰트를 유지하고 운영 브라우저 검사만 추가한다.
