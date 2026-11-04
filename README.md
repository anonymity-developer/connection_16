# TAG ME

SSAFY 16기 네트워킹 이벤트. 현장의 NFC/QR로 다섯 미션을 열고 **SSAFY** 글자를 수집하며 동료를 발견하는 서비스입니다.

## 문서

- **[Codex 개발 환경과 스킬](docs/codex-setup.md)** · [현재 페이즈](docs/progress.md): Taste/Playwright/CI 스킬 원문, AGENTS 규칙, 실행 도구와 검증 제약
- **[전체 개발 기준서와 읽는 순서](docs/README.md)**: 대화 없이 이해할 수 있는 문서 목차, 확정 정책, 현재 구현과 미확정 사항
- [기획 개요](docs/overview.md): 목적, 사용자 흐름, 다섯 미션, 공개 범위
- [유저 플로우](docs/user-flows.md): 전체 진입, 인증, 다섯 미션, 검수, 발견 그래프, 완료/시상과 재접속 분기
- [화면과 UI/UX](docs/ui-ux.md): 화면/라우트 목록, 모바일 디자인, 입력/상태/모션 및 화면별 검증 기준
- [인증 설계](docs/auth.md): Mattermost 아이디 + 가입 시 설정한 비밀번호 로그인, 가입/변경/재설정 DM 인증
- [구현 계획](docs/implementation-plan.md): 개발 단계, 트랜잭션, 검수, 시상, 검증 기준
- [아키텍처](docs/architecture.md): 실행 구성, 도메인, 보안, 서버 처리 시퀀스와 상태 전이
- [데이터 모델](docs/data-model.md): 적용된 스키마, 후속 테이블 사전, 키/인덱스와 마이그레이션
- [전체 도메인 ERD](docs/erd.md): 계정, 미션, 콘텐츠/검수, 게임, 성향, 시상, 신고/동의/감사의 컬럼과 관계
- [API 계획](docs/api.md): 현재 API와 앞으로 구현할 계약
- [개발 및 운영 가이드](docs/development.md): 실행, 환경변수, 테스트, 백업
- [행사 운영](docs/operations.md): 태그 발급/설치, 개장/마감, 검수/신고/재도전, 시상과 종료

## 현재 구현 범위

React + TypeScript 시작 화면, Spring Boot 기본 서버, 보안 필터, CSRF 토큰 API, Redis 세션 설정, Flyway 기반 핵심 테이블과 5개 미션 시드, Docker Compose, 개발/운영용 Caddy, CI를 준비했습니다.

**페이즈 2 인증 API와 화면 연결을 구현했습니다.** Mattermost 아이디 + TAG ME 비밀번호 로그인, 가입/변경/재설정의 숫자 6자리 DM 인증과 세션 무효화를 제공합니다. 기본 `AUTH_ENABLED=false`이며 실제 Mattermost 토큰/지정 채널/DM 권한은 별도로 검증해야 합니다. [인증 구현과 설정](docs/auth.md)을 확인하세요. 페이즈 3에서 프로필/동의, 태그 해금, 서버 수집판과 SHOW ME를 연결했습니다. 성향/소원/탭/응원 수행, AI 검수, 탐색과 시상은 후속 개발 대상입니다. `/api/v1/meta`는 초기 연결 확인용이며 사용자별 미션 상태가 아닙니다.

## 빠른 시작: 전체 Docker 실행

필수: Docker Engine/Desktop + Compose v2. `cp` 대신 Windows PowerShell에서는 `Copy-Item`을 사용해도 됩니다.

```bash
cp .env.example .env
# .env의 DB_PASSWORD, DB_ROOT_PASSWORD를 변경하세요.
docker compose -f compose.yml -f compose.dev.yml --profile app up -d --build --wait
```

- 시작 화면: http://localhost:8088
- API 확인: http://localhost:8088/api/v1/meta
- 내부 의존성 확인: http://localhost:8080/actuator/health/readiness
- 중지: `docker compose -f compose.yml -f compose.dev.yml --profile app down`

`down`은 데이터를 유지합니다. `down -v`는 MySQL과 Redis 볼륨까지 삭제하므로 일반 중지에 사용하지 마세요.

## 빠른 시작: IDE 개발

JDK 21, Node.js 24가 필요합니다. DB와 Redis만 Docker에서 실행하고 백엔드와 프론트는 IDE에서 실행합니다.

```bash
docker compose -f compose.yml -f compose.dev.yml up -d --wait mysql redis
```

백엔드 터미널:

```bash
cd backend
# .env와 같은 값을 현재 터미널 또는 IDE의 환경변수로 설정하세요.
export DB_PASSWORD='your-local-password'
./gradlew bootRun
```

Windows PowerShell에서는 `$env:DB_PASSWORD='your-local-password'`, `./gradlew.bat bootRun`을 사용합니다. Spring Boot는 저장소 루트 `.env`를 자동으로 읽지 않습니다. DB 이름, 사용자, 포트를 바꿨다면 관련 환경변수도 함께 지정하세요.

프론트엔드 터미널:

```bash
cd frontend
npm ci
npm run dev
```

http://localhost:5173 접속. `/api` 요청은 Vite가 백엔드로 프록시합니다. 개발 방식 두 가지를 동시에 실행하면 API 포트가 충돌하므로 한 가지를 선택하세요.

## 검증

```bash
cd frontend
npm run lint
npm run build
```

```bash
cd backend
./gradlew test bootJar
./gradlew integrationTest  # Docker 필요; 실제 MySQL/Redis와 마이그레이션 검증
```

CI는 프론트 lint/build, 백엔드 단위/통합 테스트와 두 Docker 이미지의 빌드 및 전체 연결 검증을 수행하도록 구성되어 있습니다. 세부 운영 절차와 아직 확정하지 않은 사항은 문서를 참고하세요.

프로필·동의·태그·서버 수집판 구현은 [참여 계약](docs/participation.md), 현재 요청/응답은 [참여 OpenAPI](docs/participation.openapi.yaml)를 따른다. 실제 행사 일정/동의 문구/태그는 별도로 설정해야 한다.
