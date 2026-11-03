# Codex 개발 환경과 스킬 운용

이 문서는 TAG ME를 다른 Codex 세션/로컬 IDE에서도 이어 개발하기 위한 설정이다. 링크 목록이 아니라 저장소 안의 스킬 원문/실행 도구/작업 규칙을 기준으로 한다. 조사 기준일은 2026-11-03이다.

## 1. 적용한 구성

| 파일/폴더 | 역할 |
| --- | --- |
| `AGENTS.md` | 프로젝트 공통 규칙, 문서/스킬 라우팅, 실제 검증/커밋 기준 |
| `frontend/AGENTS.md` | Taste 필수 적용, 앱/페이지 분류, 반응형/폼/브라우저 검증 |
| `backend/AGENTS.md` | Java/Spring 인증, DB 제약/잠금/검수/테스트 규칙 |
| `infra/AGENTS.md` | Compose/공개 포트/백업/배포 검증 |
| `.agents/skills/` | 아래 세 스킬의 실제 원문과 필요한 scripts/references/assets/library |
| `.agents/skills.lock.json` | 출처 URL, 고정 커밋, 라이선스, 파일별 SHA-256 |
| `scripts/verify-skills.mjs` | 로컬 스킬 리소스가 고정 원본과 일치하는지 검사 |
| `scripts/taste.mjs` | 작업 디렉터리에 무관하게 저장소 루트에서 Taste 실행 |
| `scripts/codex-doctor.mjs` | Node/npm/Java/Docker/Chrome/gh/스킬 파일 준비 상태 점검 |
| `docs/progress.md` | 큰 페이즈와 현재 실제 진척, 미완료/차단 검증 |

프로젝트 스킬의 위치 `.agents/skills`와 계층별 AGENTS.md는 [OpenAI 공식 customization 문서](https://learn.chatgpt.com/docs/customization/overview)의 저장소 범위를 따른다. 짧은 상시 규칙만 AGENTS에 두고 긴 도메인 설계는 docs, 선택 시 필요한 절차는 SKILL.md로 나눈다. 현재 세션의 스킬 목록에 표시되지 않아도 AGENTS가 지시한 경로를 직접 읽고 사용할 수 있다.

## 2. 선정한 스킬과 제외한 범위

| 스킬 | 사용 작업 | 선정 이유/경계 |
| --- | --- | --- |
| `design-taste-frontend` | UI 새 구축, 수정, 디자인 리뷰 | 사용자 지정 Taste. 방향 생성, 실제 브라우저 검사, 화면 비평 절차 포함. 모바일 앱 화면과 공개 페이지의 절차가 다름 |
| `playwright` | 브라우저 폼/이동/팝업/스크린샷 | OpenAI 공식 CLI 중심 워크플로. 스킬 파일과 Playwright 실행 패키지/브라우저 설치는 별개 |
| `gh-fix-ci` | GitHub Actions 실패 원인과 회귀 확인 | OpenAI 공식 PR 검사/로그 워크플로. `gh` 인증이 없으면 현재 연결된 GitHub 앱으로 조회/수정 진행 |
| 백엔드 전용 외부 스킬 | 이번에 추가하지 않음 | 공식 `security-best-practices`의 지원 언어는 Python/JS·TS/Go이며 Java/Spring 점검을 커버한다고 주장하지 않음. 프로젝트 backend/AGENTS와 auth/API/ERD/실제 통합 테스트로 제약을 구체화 |
| 호스팅/디자인 툴 스킬 | 현재 필수 아님 | 단일 VM Compose와 기존 React 프로젝트이므로 Netlify/Vercel/Figma로 작업 환경을 바꾸기 위해 설치하지 않음 |

전체 스킬을 매번 로드하지 않는다. UI에는 Taste, 브라우저 상호작용에는 Playwright, 실패한 GitHub 검사에는 gh-fix-ci만 필요한 자료와 함께 읽는다. 스킬을 넣었다고 권한이나 외부 인증이 생기지 않는다.

## 3. 고정 출처와 업데이트 정책

| 스킬 | 출처 | 고정 커밋 |
| --- | --- | --- |
| Taste 2.1.5 | [Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill) / `skills/taste-skill` | `12af471f627f866411b4bd785b19646d3672784c` |
| Playwright | [openai/skills](https://github.com/openai/skills/tree/49f948faa9258a0c61caceaf225e179651397431/skills/.curated/playwright) | `49f948faa9258a0c61caceaf225e179651397431` |
| GitHub CI | [openai/skills](https://github.com/openai/skills/tree/49f948faa9258a0c61caceaf225e179651397431/skills/.curated/gh-fix-ci) | `49f948faa9258a0c61caceaf225e179651397431` |

원본 라이선스/NOTICE/아이콘/실행 의존 리소스를 유지한다. Taste는 SKILL.md만 복사하면 library/scripts 참조가 끊어지므로 전체 스킬 폴더를 포함한다. 코드가 upstream 스크립트를 조용히 고치거나 실행 때마다 최신 버전을 다운로드하지 않는다. vendored 파일은 `.agents/skills/.gitattributes`로 줄바꿈까지 보존한다.

업데이트는 상위 변경/라이선스/실행 스크립트를 검토 → 새 고정 커밋의 전체 폴더 반영 → lock 해시 갱신 → 원본 무결성/스크립트/브라우저 확인 → 독립 커밋 순서다. lock 자체도 변경 리뷰 대상이며 이 검사는 코드 안전성을 자동 보증하는 보안 인증이 아니다.

## 4. 사용 순서

저장소 루트에서 시작한다. 개인 전역 config의 모델/승인 정책/접근 권한을 이 저장소가 자동 변경하지 않는다.

```bash
node scripts/codex-doctor.mjs
node scripts/verify-skills.mjs
```

doctor는 준비 상태를 보고하며 기본 모드의 종료 0은 모든 도구가 준비됐다는 뜻이 아니다. `--strict`는 필요한 Node 24/npm/스킬 원본 검사 실패 시 비정상 종료한다. Docker, 브라우저 실제 실행, CLI 외부 인증은 각각 별도 검증이 필요하다.

### UI 구축/수정

1. Taste SKILL.md를 읽는다. 앱 화면이면 `references/app-screens.md` 전체를, 공개 이벤트 소개 페이지면 페이지 절차와 필요한 copy/layout 자료를 읽는다.
2. 기존 화면 변경이면 변경 전 상태를 먼저 실제 브라우저로 캡처/검사한다. 기존 코드가 이미 있다는 이유만으로 현재 스타일을 확정 브랜드처럼 고정하지 않는다.
3. draw 스크립트의 지원 종류를 확인하고 요구사항에 맞는 방향을 선택한다. 실제 사용자/이벤트 수치/성향 문항을 만들거나 관련 없는 사진으로 기능을 포장하지 않는다.
4. 디자인 토큰과 실제 입력/상태/복구를 구현한다. 외부 UI 스킬보다 TAG ME의 인증/해금/검수/공개 범위 규칙이 우선한다.
5. lint/build 후 **운영 빌드**를 브라우저로 검사하고 스크린샷을 직접 본다. Taste의 FAIL/WARN과 폼/내비게이션 동작을 확인하고 수정한다.

```bash
node scripts/taste.mjs draw --list sectors
node scripts/taste.mjs draw --app --list kinds
node scripts/taste.mjs lint frontend/src
# 앱 방향 생성의 상세 인자는 app-screens.md를 읽은 후 선택한다.
node scripts/taste.mjs inspect http://127.0.0.1:4173 --app --widths 1440,768,390
```

운영 빌드 서버는 `cd frontend && npm run build && npm run preview -- --port 4173`으로 연다. 360/430 폭, OTP 앞자리 0/붙여넣기, 비밀번호 표시, 입력 오류, 뒤로가기, dialog Escape/포커스, 직접 미해금 경로, reduced motion과 dev fixture 배제를 추가 확인한다. inspector 실행 불가 시 다른 사용 가능한 브라우저 도구로 캡처/비평하고 자동 검사 미실행을 남긴다. 실패를 숨기기 위한 `--allow`나 광범위한 규칙 해제를 사용하지 않는다.

### 브라우저 상호작용

Node/npm/npx와 Playwright CLI 및 브라우저가 필요하다. 원본의 wrapper는 `npx --package @playwright/cli`를 사용하므로 초기 패키지가 없으면 npm 네트워크 접근도 필요하다. 스킬 원본을 고정한 것과 실행 CLI 버전을 고정한 것은 다르다. 팀 환경에 CLI를 정식 도입할 때는 검증한 패키지 버전을 별도로 고정한다.

```bash
bash .agents/skills/playwright/scripts/playwright_cli.sh --help
bash .agents/skills/playwright/scripts/playwright_cli.sh open http://127.0.0.1:4173
bash .agents/skills/playwright/scripts/playwright_cli.sh snapshot
# 최신 snapshot의 실제 ref를 사용해 click/fill/press/screenshot 진행
```

없는 element ref를 지어내지 않는다. 이동/모달 변경 후 다시 snapshot을 얻는다. 자동화 결과는 ignored `output/playwright/`, Taste 결과는 ignored `.taste-skill/`에 두고 개인정보/브라우저 프로필을 커밋하지 않는다.

### CI 장애

GitHub 연결이 있는 현재 작업 모드에서는 플러그인의 run/job/commit/파일 도구로 조회한다. 로컬 `gh` 인증은 별도이며 토큰을 대화에 붙여넣게 요구하지 않는다. CLI가 준비된 환경에서는 다음 원본 도구도 사용할 수 있다.

```bash
gh auth status
python .agents/skills/gh-fix-ci/scripts/inspect_pr_checks.py --repo . --pr <실제-PR-번호> --json
```

실패한 job과 실제 로그를 찾고 해당 변경의 회귀 검사만 실행한다. 이미 허용된 구현/수정 범위는 세션의 사용자 지시를 따른다. 새로운 외부 서비스/배포/연락을 스킬의 일반 예시만으로 실행하지 않는다. PR이 없거나 실패하지 않은 작업에서 가짜 PR/오류를 만들어 실행하지 않는다.

## 5. 현재 환경에서 직접 확인한 결과

| 항목 | 확인 결과 |
| --- | --- |
| 스킬 원본 | 세 폴더와 모든 참조 리소스를 고정 커밋에서 복사, 해시 검사 도구 포함 |
| Taste draw | 분야 목록과 앱 종류 목록 명령 실제 실행 성공. 전체 디자인 검수 완료를 의미하지 않음 |
| Taste lint | 프론트 초안 정적 검사 실제 실행, FAIL 반환. 기존 ESLint/build 성공과 구분하며 UI 개선 대상으로 남김 |
| Taste inspect | Chromium 실행 시 컨테이너의 socket 권한 오류로 중단. 화면/시각 품질 통과로 기록하지 않음 |
| Playwright CLI | npx 존재. offline 실행 시 `@playwright/cli` 캐시 없음으로 실행 불가. CLI 설치가 완료됐다고 표시하지 않음 |
| 대체 로컬 Playwright | 런타임 모듈은 있으나 headless browser 실행 파일 없음. 라이브러리 존재와 브라우저 구동을 구분 |
| GitHub | 연결된 GitHub 앱 조회/쓰기와 기존 CI 성공 확인. gh CLI는 별도 미인증 |
| 환경 doctor | Node 24/npm/Java 21/원본 해시 확인. Docker daemon은 사용 불가하므로 로컬 통합 검증은 차단 |
| 개인 스킬 목록 | 원본 Taste 패키지 등록이 서비스 측에서 실패해 완료되지 않음. 이 저장소의 공유 스킬 파일과 명시적 로드는 별개로 사용 가능 |

위 상태는 조사 시점의 이 환경에 대한 기록이다. 다른 머신에서는 doctor와 실제 smoke를 다시 실행한다. 프로젝트 공유 스킬은 `.agents/skills`에 있고, 개인 스킬 목록에 표시됐다고 주장하지 않는다.

## 6. 이후 실제로 작성/연결할 산출물

| 페이즈 | 다음 산출물 | 완료 근거 |
| --- | --- | --- |
| UI | Taste 방향/전후 캡처/비평, 모바일 공통 컴포넌트와 실제 입력/상태 테스트 | 운영 빌드와 브라우저 동작/화면 검증 |
| 인증 | username/credentials/consents 새 migration, PasswordEncoder, Mattermost Adapter, purpose별 Redis challenge, 세션 version 검사, 요청/응답 OpenAPI | DM 권한 검증, 가입/일반 로그인/변경/재설정/이전 세션 차단 통합 테스트 |
| 태그/미션 공통 | 운영 토큰 발급/해시 조회, 서버 해금 검사, 프로필/수집판/공통 완료 서비스 | 직접 API 우회 차단, 1회 제한/동시 완료/롤백 테스트 |
| 개별 미션/검수 | 슬롯/revision/job/receipt migration, 검수 Adapter/작업자, SYNC 점수 버전/게임 검증 | 늦은 판정/수정 경합, 서버 결과 불변/중단/재도전 검증 |
| 탐색/운영 | 공개 필터/추천/방향 방문/그래프, 관리자/시상 snapshot, 부하/복원/배포 절차 | 실기기 전체 흐름, 시상 정산과 운영 검증 |

이미 auth/API/ERD에 있는 설계는 중복된 대형 SKILL.md로 옮기지 않는다. 실제 반복 작업이 드러나면 작은 프로젝트 스킬/스크립트로 추가한다. 미구현 도메인의 빈 컨트롤러나 가짜 외부 호출을 스킬 준비의 산출물로 만들지 않는다.

## 7. 확인한 원문

- [OpenAI 공식: customization, AGENTS와 저장소 스킬](https://learn.chatgpt.com/docs/customization/overview)
- [OpenAI 공식: AGENTS.md 적용과 계층](https://learn.chatgpt.com/docs/agent-configuration/agents-md)
- [Taste 원본 설치/구조](https://github.com/Leonxlnx/taste-skill/blob/12af471f627f866411b4bd785b19646d3672784c/README.md)
- [Taste SKILL.md](https://github.com/Leonxlnx/taste-skill/blob/12af471f627f866411b4bd785b19646d3672784c/skills/taste-skill/SKILL.md)
- [Playwright 원본](https://github.com/openai/skills/blob/49f948faa9258a0c61caceaf225e179651397431/skills/.curated/playwright/SKILL.md)
- [gh-fix-ci 원본](https://github.com/openai/skills/blob/49f948faa9258a0c61caceaf225e179651397431/skills/.curated/gh-fix-ci/SKILL.md)
- [공식 보안 스킬의 지원 범위](https://github.com/openai/skills/blob/49f948faa9258a0c61caceaf225e179651397431/skills/.curated/security-best-practices/SKILL.md)
