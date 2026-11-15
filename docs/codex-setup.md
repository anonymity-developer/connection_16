# Codex 개발 환경

프로젝트 공통 규칙은 [루트 AGENTS.md](../AGENTS.md), 작업별 규칙은 [프론트](../frontend/AGENTS.md)·[백엔드](../backend/AGENTS.md)·[인프라](../infra/AGENTS.md)에 둔다. 서비스 설명과 에이전트 실행 지침은 분리하며, 개인 설정·인증·로그는 저장소에 넣지 않는다.

## 공유하는 설정

| 경로 | 역할 |
| --- | --- |
| `.agents/skills/` | 저장소에 고정한 Taste·Playwright·GitHub CI 스킬 원문과 실행 리소스 |
| `.agents/skills.lock.json` | 출처 커밋·라이선스·파일 해시 |
| `scripts/verify-skills.mjs` | 고정 원본 무결성 검사 |
| `scripts/codex-doctor.mjs` | Node/npm/Java/Docker/브라우저/인증 준비 상태 확인 |
| `scripts/taste.mjs` | 저장소 기준 Taste 실행 |

스킬 출처와 고정 버전은 lock 파일을 기준으로 한다. 원문·라이선스·참조 리소스를 함께 유지하고, 갱신은 별도 변경으로 검토한다.

## 시작 순서

1. `git status`와 [현재 구현 상태](progress.md)를 확인하고 [문서 안내](README.md)에서 필요한 계약만 읽는다.
2. 관련 AGENTS와 필요한 스킬의 `SKILL.md`를 실제로 읽는다. UI는 Taste의 앱 참조, 화면 캡처는 Playwright, 실패한 Actions는 gh-fix-ci를 사용한다.
3. 아래 준비 검사를 실행하고 [개발 가이드](development.md)에 따라 필요한 런타임을 준비한다.

```powershell
node scripts/codex-doctor.mjs
node scripts/verify-skills.mjs
```

doctor의 기본 종료 성공은 Docker·브라우저·외부 인증이 모두 가능하다는 뜻이 아니다. 실제 실행 결과를 별도로 확인한다. Java 21·Node 24를 사용하고, 브라우저 패키지/Chromium은 스킬 파일과 별도로 설치한다. GitHub 앱과 로컬 `gh` 인증도 서로 별개다.

## 작업과 산출물

- 기존 사용자 변경과 실행 중인 로컬 DB를 보존한다. fixture API의 재시작도 합성 상태를 다시 준비할 수 있으므로 먼저 영향을 확인한다.
- 변경에 맞는 lint/build/단위·통합·브라우저 검사를 수행하고 실제 통과한 범위만 기록한다. UI는 개발 화면만으로 완료하지 않고 운영 빌드와 모바일/PC 캡처를 확인한다.
- 일반 캡처·추적은 ignored `output/playwright/`, Taste 결과는 `.taste-skill/`, 개인 실행 자료는 `.codex-log/`에 둔다. README용 합성 화면만 개인정보·URL·콘솔 노출을 검토한 뒤 `docs/assets/`에 선별한다.
- 정책 변경은 해당 계약/OpenAPI와 함께 갱신한다. 진행 문서에는 현재 상태와 남은 확인만 두며 일회성 로그를 누적하지 않는다.
- 브랜치에 커밋하고 PR로 전달한다. 병합·배포는 현재 사용자의 승인 범위를 따른다. 설정 파일을 추가해도 외부 인증이나 실행 권한이 자동으로 생기지 않는다.
