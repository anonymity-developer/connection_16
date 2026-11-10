# TAG ME AI 검수·네트워킹 작업 보고

## 기준 및 보존

- 작업 브랜치: `feat/network-graph`. 시작 HEAD `5a635997f4f8ce2499b91bd7061f64b1970ffdd7`.
- 시작 시 원격 main과 feat/network-graph가 같은 HEAD임을 `git ls-remote`로 확인. 병합 불필요.
- 최초 검토 보고 후 사용자가 작업 브랜치 commit/push를 승인했다. PR/merge는 수행하지 않는다. 기존 Flyway V1~V9, 인증, 미션 완료·검수 트랜잭션을 유지. 스키마 변경 없음.
- React 19/TypeScript/Vite, Java 21/Spring Boot 3.5, MySQL 8.4/Redis. 기존 npm lockfile 유지.

## 기존 구현 분석

| 영역 | 확인한 구현 | 남은 검증/정책 |
| --- | --- | --- |
| 인증 | 지정 Mattermost 채널 멤버십, DM OTP, TAG ME 비밀번호, CSRF, 세션 버전 | 실제 채널이 16기 서울인지 운영 확인, 실제 DM 권한 |
| 프로필 | Mattermost 이름·지역·반·사진, 닉네임 필수, 관심사 선택 | 실제 사진/프로필 API 권한 |
| 미션 | NFC/QR 서버 해금, 수집판, 공통 완료 경로, 콘텐츠 계정별 슬롯 | 현장 NFC/QR 및 실기기 |
| SYNC | 버전별 문항/점수/축 동점, 최초 결과 저장 | 초안은 2축·6문항·4유형. 16유형 확정본 아님; 임의 변경하지 않음 |
| FAST TAP | 5초 준비·10초 게임, 공식 슬롯/서버 시각/검증 | 기기별 공정성·현장 지연·공개 시상 |
| 콘텐츠 | 소원·응원 1개, 댓글 다수, 승인본/수정본 분리, 하트 PK | 외부 자동 검수 어댑터가 기존에는 없음 |
| 관리자 | 검수 승인/반려/재처리, 숨김/복구, 감사 | 시상 집계/스냅샷은 미구현 |
| 추천 | 닉네임/관심사 검색, 미방문→공통 관심사→동일 버전 유형→ID | 기존 정렬 유지; 화면에서 해당 이유 모두 표시 |
| 그래프 | 내 직접 방문 상대·방향·통계, 공개 자격 필터 | 이번 변경으로 간선 가중치·홈 그래프·노드 이동 추가 |

## 추가 구현

- `HttpModerationProvider`: GMS Chat Completions JSON 응답과 OpenAI Moderation results 응답을 각각 파싱. HTTP 연결 3초/응답 12초 제한, redirect 금지, HTTPS 설정 확인.
- GMS는 gpt-5-nano, `max_completion_tokens`, JSON 응답, minimal reasoning 사용. 불완전 출력/거절 응답/알 수 없는 판정/HTTP 오류/한도 초과/timeout을 REVIEW_REQUIRED로 처리. 예외·응답·본문·비밀값을 로그에 남기지 않음.
- 기존 ModerationProvider/worker의 revision별 job, lease/fence, 관리자 판정, 승인 포인터와 완료 경로 재사용. 동일 제출 멱등성과 완료 중복 방어 보존. 외부 호출의 exactly-once는 보장하지 않음: 프로세스 중단/lease 재회수 후 재호출 가능. 어댑터 내부 자동 재시도는 없음.
- GMS 프롬프트는 욕설/비방/괴롭힘/혐오/위협/스팸/개인정보를 검사하고 일반적 걱정·불안·부정 감정을 허용하도록 지시. 품질은 실제 한국어 평가셋으로 추가 검증 필요.
- OpenAI 어댑터는 준비 상태이며 기본 비활성. `flagged=false`만으로 TAG ME의 스팸·개인정보·욕설 정책을 모두 통과했다고 볼 수 없어 수동 검수로 보냄. flagged 역시 맥락 검토로 보냄. 추가 정책 검사 및 실환경 검증 후 전환해야 함.
- 공개 그래프는 기존 방문 간선을 유지. 방향별 최초 방문 +1, 상호 방문 +2 추가, **본인이 누른 승인된 비익명 응원 카드 하트** +2. 최대 6, LIGHT(1~2)/MEDIUM(3~4)/STRONG(5~6). 현재 상태 재계산이라 반복 방문·하트 취소/재등록으로 누적되지 않음.
- 상대의 좋아요 신원은 기존에 공개되지 않으므로 반영하지 않음. 점수는 조회자 관점이며 상대 화면과 다를 수 있음. 방문 없는 하트만으로 새 간선은 만들지 않음.
- 소원이 항상 익명이므로 댓글/상호 댓글 점수는 **의도적으로 제외**. 점수만 반환해도 소원 작성자 유추가 가능함. 댓글 제한·시상용 유효 댓글 집계와 그래프는 분리해야 함.
- 홈 하단 그래프, 노드의 44px 링크, 키보드 접근, 기존 상세 POST 경로, 간선 굵기·명도 차등. 기존 화면/미션 흐름 유지.

## 환경변수

| 이름 | 설정 |
| --- | --- |
| TAGME_MODERATION_PROVIDER | 기본 NONE; 실제 GMS 사용 권한/설정 확인 후 GMS. OPENAI는 별도 검증 전 사용하지 않음 |
| TAGME_GMS_BASE_URL | SSAFY에서 제공한 OpenAI 호환 base URL. `/chat/completions` 앞부분. HTTPS 필수; 실제 주소 추정하지 않음 |
| GMS_KEY | 서버 환경에만 실제 키 설정 |
| OPENAI_API_KEY | OpenAI 검증 준비 시 서버 환경에만 설정 |

`.env.example`에는 빈 키만 추가. 기존 `AI_API_KEY`는 보존하지만 새 어댑터에서 사용하지 않음. Docker Compose api 서비스에 전달되도록 연결. IDE 실행은 `.env` 자동 로딩이 없으므로 환경변수로 주입해야 함. 키를 프론트 VITE 변수로 넣지 말 것.

2026-11-05~06 GMS 사용 계획 및 11-07 이후 OpenAI 전환은 운영 계획일 뿐, 자동 날짜 전환은 구현하지 않음. GMS 행사 이용 허용/키/호출 한도는 운영 계정 접근이 없어 확인하지 못함. 실제 AI 호출·운영 DB 변경·DM 발송 없음.

## 확정 시상 정책과 미구현 범위

이번 지시 기준 5부문: 최초 완성 1명, 댓글왕 1명, 인기 응원 1명, FAST TAP 1명, 랜덤 참여상(인원/조건 미정).
댓글왕은 타인 소원별 유효 승인 댓글을 DISTINCT 소원으로 집계. 경쟁 동점은 **최초 프로필 등록 시각** 우선. 기존 완료 처리 순번을 수상 순위로 사용하면 안 됨.
현재 시상 집계 API/스냅샷이 없으므로 시상 반영까지 구현됐다고 보지 않음. 프로필 최초 등록 근거와 유효 콘텐츠의 정산 기준을 기존 receipt에 맞춰 후속 구현해야 함.

## 검증

- 수정 전: 프론트 단위 12개, ESLint, 운영 빌드 통과. 원본 HEAD를 별도 임시 폴더에 추출하여 백엔드 단위 21개와 bootJar도 통과(환경 설치 후 기준 상태 보완). 스킬 원본 무결성 검사 통과.
- 수정 후: 프론트 단위 13개, ESLint, 운영 빌드 통과. 변경 그래프 소스 정적 UI 검사 0 FAIL/0 WARN.
- 추가 서버 테스트: GMS/OpenAI 응답·정상/거절/개인정보/불완전 응답·한도/timeout·키 누락, 관계 점수, 실제 DB의 취소/재등록·익명·숨김·미승인·상대 좋아요 비노출.
- 브라우저 계약 검사에 가중치 선·노드 이동·홈 표시 추가. 실행 결과는 최종 갱신 아래 참조.
- Java/Gradle/Docker/브라우저 환경 제약으로 실행하지 못한 검사를 통과로 취급하지 않음.

## 다음 실행 명령

```bash
cd frontend
npm ci
npm run test:unit
npm run lint
npm run build
npx playwright install chromium
npm run test:discovery-ui
cd ../backend
./gradlew test bootJar
./gradlew integrationTest
```

OpenAI 응답 계약 참고: https://developers.openai.com/api/docs/guides/moderation
GMS gateway의 실제 지원 파라미터는 별도 운영 테스트 필요. Mock 응답 테스트는 실연동 성공 증거가 아님.

## 최종 검증 갱신

- Java 21 및 Gradle 8.14.3을 임시 환경에 설치하고 저장소 버전은 변경하지 않았다.
- 백엔드 `test bootJar` 성공: 단위 테스트 **28개 통과**, 실행 JAR 생성. 새 어댑터/가중치 테스트 7개 포함.
- `integrationTest` 실행: **8개 테스트 클래스가 Docker 환경 초기화에서 실패**. 실제 MySQL/Redis 통합·동시성 및 새 가중치 SQL 실행 검증은 미완료. 테스트 생략/대체 DB 사용 없음.
- 프론트 단위 **13개 통과**, lint/build 성공. 탐색 7그룹(새 가중치/노드/홈 포함), 콘텐츠 7그룹, 반응 7그룹, SYNC/FAST TAP 6그룹, 인증 5그룹 브라우저 계약 검사 성공.
- 참여 6그룹·관리자 10그룹의 동작 검사는 성공했으나 각 스크립트 마지막 별도 시각 검사는 일반 Chrome의 `socket() failed: Operation not permitted`로 완료하지 못했다. 따라서 해당 전체 스크립트는 실패 상태다.
- 탐색/관계 화면은 headless-shell로 360/390/430/768/1440px 실행했다. 한글 폰트 설치 후 모바일·데스크톱 캡처 확인. 실기기 iOS/Android와 운영 API는 미검증.
- 기존 다른 화면 테스트에는 홈의 새 graph GET에 대한 빈 그래프 대역만 추가. 실제 데이터/API 호출을 흉내 내 운영 기록을 생성하지 않는다.
- Compose 개발/운영 config 및 smoke는 Docker 부재로 미실행.
- 기존 파일 삭제/마이그레이션 수정 없음. `git diff --check`, 원본 스킬 무결성 검사 통과.

## 주요 변경 파일

| 구분 | 파일 |
| --- | --- |
| 검수 | backend/src/main/java/com/tagme/content/HttpModerationProvider.java, ModerationConfiguration.java |
| 관계 계산 | backend/src/main/java/com/tagme/discovery/RelationshipWeight.java, DiscoveryRepository.java, DiscoveryModels.java |
| 서버 테스트 | HttpModerationProviderTest.java, RelationshipWeightTest.java, DiscoveryIntegrationTest.java |
| 설정 | .env.example, compose.yml, backend/src/main/resources/application.yml |
| 그래프/추천 화면 | frontend/src/features/discovery/{HomeNetwork,NetworkFigure}.tsx, relationship.ts, api.ts, components.tsx, pages/{Home,DiscoveryGraph}.tsx, styles/global.css |
| 화면 검사 | frontend/tests/relationship.test.mjs, frontend/scripts/check-{discovery,content,reactions,results,participation,operations}-ui.mjs |
| 계약/보고 | docs/{overview,implementation-plan,content,discovery,progress,network-moderation-handoff}.md, discovery.openapi.yaml |
