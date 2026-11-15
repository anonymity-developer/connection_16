# 개발 가이드

## 준비

- Docker Desktop/Engine과 Compose v2
- IDE 실행 시 Java 21, Node.js 24, npm lockfile 사용
- 저장소의 `.env.example`을 복사한 개인 환경 파일

`.env`, `.env.production`, 실제 비밀번호·토큰은 Git에 올리지 않는다. 운영 데이터와 fixture 환경을 절대 함께 사용하지 않는다.

## fixture로 전체 흐름 확인

fixture 이미지는 실제 Mattermost·NFC 없이 테스트 본인 1명과 합성 동료 20명, 서로 다른 미션/탐색/콘텐츠 상태를 전용 `tagme_fixtures` DB에 준비한다. 일반·운영 JAR에는 fixture 소스가 포함되지 않는다. 기본 더미에는 미션별 콘텐츠·SYNC 결과·탭 순위·관계가 포함된다. `test`는 첫 S를 가진 시작 상태로 준비한다.

fixture API를 재시작하면 행사 설정·더미 참여/관계가 재구성되고 테스트 계정의 미션 상태도 다시 준비된다. 볼륨을 남긴다는 이유만으로 모든 테스트 상태가 유지된다고 가정하지 않는다. 진행 중인 사용자 테스트를 보존하려면 API를 재시작하지 말고 필요한 web 서비스만 갱신한다. `TAGME_FIXTURES_CHEERS=true`는 응원 전용 추가 합성 카드 28개를 준비하는 선택 옵션이다.

```powershell
Copy-Item .env.example .env.fixtures
# .env.fixtures에 로컬 전용 DB_PASSWORD와 DB_ROOT_PASSWORD 설정

docker compose --env-file .env.fixtures --project-name tagme-fixtures `
  -f compose.yml -f compose.dev.yml -f compose.fixtures.yml `
  --profile app up -d --build --wait

docker compose --env-file .env.fixtures --project-name tagme-fixtures `
  -f compose.yml -f compose.dev.yml -f compose.fixtures.yml `
  --profile app ps
```

- 기본 주소: `http://localhost:8088`
- 참가자: `test` / `test1234` (직접 심은 로컬 fixture 전용 기존 자격증명. 실제 가입 비밀번호 정책 8~20자와 별개)
- 별도 스택과 포트가 겹치면 `.env.fixtures`에 `WEB_PORT=8090`, `API_PORT=8082`, `MYSQL_PORT=13309`, `REDIS_PORT=6382`를 지정한다.
- 관리자 테스트가 필요하면 `.env.fixtures`에 `ADMIN_BOOTSTRAP_ID=admin:fixture`와 직접 정한 15~64자 로컬 비밀번호를 추가한다. 공용 관리자 비밀번호는 저장소에 두지 않는다.

로컬 태그는 fixture에서만 유효하다.

| 미션 | 경로 |
| --- | --- |
| SYNC ME | `/t/AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE` |
| A WISH COME TRUE | `/t/AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI` |
| FAST TAP | `/t/AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM` |
| YOU GOT THIS | `/t/BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ` |

권장 확인 순서:

1. 참가자 로그인 후 수집판과 프로필을 확인한다.
2. 네 로컬 태그를 하나씩 열어 각 미션의 잠금 해제를 확인한다.
3. 미션 참여 후 글자 획득 안내와 수집판 복원을 확인한다.
4. 소원/댓글, 응원/하트, SYNC 같은 유형, FAST TAP 순위를 확인한다.
5. 동료 추천·검색·상세·방향 관계를 확인한다.
6. 선택적으로 관리자 계정으로 회원·미션·콘텐츠·감사 화면을 확인한다.

### 중지와 초기화

```powershell
# 데이터 유지
docker compose --env-file .env.fixtures --project-name tagme-fixtures `
  -f compose.yml -f compose.dev.yml -f compose.fixtures.yml `
  --profile app down

# fixture 데이터까지 삭제하고 처음부터 다시 만들 때만 -v
docker compose --env-file .env.fixtures --project-name tagme-fixtures `
  -f compose.yml -f compose.dev.yml -f compose.fixtures.yml `
  --profile app down -v
```

`down -v`는 해당 Compose 프로젝트의 볼륨을 삭제한다. 프로젝트 이름과 파일 조합이 `tagme-fixtures`인지 확인한 뒤 사용한다.

## IDE 개발

의존성만 Docker로 실행한다.

```powershell
docker compose -f compose.yml -f compose.dev.yml up -d --wait mysql redis
```

백엔드는 `backend`를 Gradle 프로젝트로 열고 DB/Redis 환경변수를 맞춘 뒤 `TagmeApplication` 또는 `./gradlew.bat bootRun`을 실행한다. 프론트는 다음처럼 실행한다.

```powershell
cd frontend
npm ci
npm run dev
```

기본 Vite 주소는 `http://localhost:5173`이다. API 포트가 다르면 실행 전 `API_PROXY_TARGET`을 설정한다.

## 검증

```powershell
node scripts/codex-doctor.mjs
node scripts/verify-skills.mjs

cd frontend
npm run lint
npm run test:unit
npm run build

cd ../backend
./gradlew.bat test bootJar
./gradlew.bat integrationTest
```

통합 테스트는 Docker의 실제 MySQL/Redis를 사용한다. 브라우저 검사와 화면 캡처는 [프론트 AGENTS](../frontend/AGENTS.md)의 실제 프로덕션 빌드 절차를 따른다. 예를 들어 `frontend`에서 `npm run test:results-ui`, `npm run test:content-ui`, `npm run test:discovery-ui`를 실행한다. 이 검사는 loopback Vite preview와 합성 API 응답을 사용하며 운영/fixture DB를 변경하지 않는다.

```powershell
# 최초 브라우저 준비 (frontend에서)
npx playwright install chromium
```

기존 README 세 화면은 로컬 fixture, 추가 게임/관계 화면은 운영 빌드 브라우저 계약 대역으로 촬영했다. 캡처 선정과 보관 기준은 [Codex 셋업](codex-setup.md)을 따른다. 개발용 9문항 예시는 `frontend/src/dev/sync-questionnaire.proposal.json`, 비교 검증용 사본은 `backend/src/test/resources/fixtures/`에 둔다. 현재 실제 설문은 V19가 등록한 9문항·8유형이며 예시 파일로 운영 설정을 대체하지 않는다. 테스트를 생략하거나 운영 외부 연동 성공으로 확대 해석하지 않는다.

## 일반 개발 스택

fixture가 필요 없는 경우:

```powershell
Copy-Item .env.example .env
docker compose -f compose.yml -f compose.dev.yml --profile app up -d --build --wait
```

기본 웹 주소는 `http://localhost:8088`이다. `.env`는 Spring Boot IDE 실행에 자동 주입되지 않으므로 IDE/터미널 환경변수도 별도로 설정한다.

운영 배포·백업·롤백은 [운영 배포](github-actions-ec2.md)와 [행사 운영](operations.md)을 따른다.
