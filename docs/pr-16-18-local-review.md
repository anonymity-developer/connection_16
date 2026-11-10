# PR #16·#17·#18 로컬 테스트

2026-11-11 최초 로컬 검증 기록이다. 작업 위치는 기존 `connection_16`, 당시 브랜치는 `review/pr-16-18-local`이다. 이후 사용자의 병합 승인과 회색 수집판 수정 요청은 [진행 기록](progress.md)의 최신 항목을 따른다.

## 가져온 변경

| PR | 고정한 커밋 | 로컬 구성 |
| --- | --- | --- |
| [#16](https://github.com/anonymity-developer/connection_16/pull/16) | `a0aab1c` | 초안 UI를 별도 개발 서버에서 비교 |
| [#17](https://github.com/anonymity-developer/connection_16/pull/17) | `3433f3c` | 최신 main 위에 적용, 기본 테스트 UI/API |
| [#18](https://github.com/anonymity-developer/connection_16/pull/18) | `b6cb527` | #17과 로컬 병합, 동료 탐색 개선 |

#17과 #18은 `docs/progress.md`만 충돌해 양쪽 기록을 모두 보존했다. #18의 기준은 이전 main `db71a13`이지만 공통 조상 기반 병합으로 최신 배포·관리자 설정을 유지했다.

#16은 #17과 홈·CSS·화면 검사 5개 파일에서 충돌했다. 합치려던 작업은 취소하고 별도 비교본을 만들었다. 두 PR은 홈 5카드 등의 변경이 중복되며, 미발견 수집판은 #16에서 회색 배경, #17에서 파스텔 배경이다. #17에는 수집 축하 효과, 미션 표시명, 일반 비밀번호 최소 8자(관리자 15자 유지), 소원·응원 제출 시 글자 지급 및 승인된 시상 근거 집계 변경도 있다. 따라서 단순 UI 패치로 취급하지 않는다.

## 접속

| 목적 | 주소 | 데이터 |
| --- | --- | --- |
| 기본 테스트 | http://localhost:8088 | #17+#18 실제 로컬 API·DB |
| 일반 화면 개발 서버 | http://localhost:5173 | 같은 로컬 API, 상태 미리보기 사용 가능 |
| 로그인 없이 미션 체험 | http://localhost:5173/experience.html | 메모리 전용, 가상 NFC·초기화·반복 가능 |
| 관리자 로그인 | http://localhost:8088/auth | `admin:tagme`, 기존 `.env` 관리자 비밀번호 사용 |
| 관리자 게임 | http://localhost:8088/admin/preview | 서버 ADMIN 확인 후 메모리 전용 미션 |
| #16 UI 비교 | http://localhost:5175 | #16 프론트엔드, API는 #17+#18 로컬 서버 공유 |

일반 사용자는 기존 TAG ME 아이디·비밀번호로 로그인한다. 가입/재설정은 기존 Mattermost 설정을 사용하며, 자동 검사에서는 실제 DM을 보내지 않았다. 관리자 비밀번호는 Git 제외 `.env.local-review`에도 기존 값 그대로 복사했다. 새 비밀값 입력은 필요하지 않다.

브라우저 F12의 기기 모드로 360/390/430px 화면을 비교할 수 있다. 서버는 loopback에 바인딩되어 있어 휴대전화에서 PC의 localhost로 접속할 수는 없다.

## 데이터와 실행

- `.env`, `.env.pr-review`, `.env.production`은 보존했다. Git 제외 `.env.local-review`만 별도로 만들었다.
- 기존 `tagme` DB를 `backups/pr-review-before-v11.sql.gz`로 백업해 새 `tagme-pr-review` 볼륨에 복원했다. 기존 사용자 11명(더미 포함)과 프로필을 유지하고, 새 DB에서만 V10·V11과 관리자 bootstrap을 적용했다. 기존 두 스택은 중지 상태를 유지했다.
- 현재 #18의 공개 조건으로 조회 가능한 프로필 11개, 방향이 있는 방문 관계 11개, 기존 성향 결과 1개를 읽기 전용 집계로 확인했다. 실제 일반 계정으로 로그인하면 기존 더미와 관계 데이터를 사용해 탐색을 확인할 수 있다.
- 새 웹/API는 8088/8080, MySQL/Redis는 13308/6381로 loopback에만 공개한다. 운영 DB·도메인·EC2는 사용하지 않는다.
- PR #16 소스 비교본은 Git 제외 `.codex-log/pr16/frontend`에 있으며, 현재 frontend의 node_modules를 junction으로 공유한다. 코드 스냅샷이므로 통합본을 수정해도 #16 소스는 바뀌지 않는다.
- 기존 볼륨을 삭제하거나 새 migration을 적용한 DB에 예전 API를 연결하지 않는다.

Docker 재시작 후 저장소 루트에서:

```powershell
docker compose --env-file .env.local-review -f compose.yml -f compose.dev.yml -f compose.small.yml --profile app up -d --wait
```

메모리 체험은 별도 터미널에서:

```powershell
cd frontend
npm.cmd run dev
```

#16 비교 서버를 다시 켜려면 다른 터미널에서:

```powershell
cd .codex-log/pr16/frontend
npm.cmd run dev -- --port 5175
```

## 확인할 동작

1. 홈의 다섯 미션 카드·수집판 상태·글자 획득 및 전체 완성 표시.
2. 체험 화면에서 프로필 저장, 가상 NFC, 성향 설문, 소원 동전·댓글, 응원·하트, FAST TAP과 초기화.
3. 실제 로그인 후 동료 찾기의 추천 이유·필터, 나의 발견 관계 그래프·프로필 이동. 개발 체험의 가상 동료 목록은 #18 실제 추천 알고리즘 검증이 아니다.
4. 관리자 로그인 후 같은 메뉴에서 현황·참가자·순위·완료 순서·미션 미리보기 이동.

## 검증 기록

- 가져온 각 PR head의 GitHub CI도 성공 상태다: [#16 CI](https://github.com/anonymity-developer/connection_16/actions/runs/42826717166), [#17 CI](https://github.com/anonymity-developer/connection_16/actions/runs/42912418282), [#18 CI](https://github.com/anonymity-developer/connection_16/actions/runs/42910729524). 각 PR 결과와 로컬 통합본 검증은 별개다.
- frontend ESLint, 단위 테스트 104개, TypeScript 및 production build 통과.
- 실제 Chromium의 UI·동료 탐색·관리자·콘텐츠·게임 결과·개발 체험·인증 검사 7개 스크립트 통과. 계약 검사는 API 대역을 쓰며 실제 Mattermost/휴대전화/NFC 성공과 구분한다.
- 실제 Docker 네 서비스 healthy, 웹/API 메타·인증 설정·OPEN 행사 응답 확인. 실제 관리자 로그인→다섯 미션 미리보기→운영 화면 조회→로그아웃 후 비공개 API 401 확인. 미리보기 전후 공식 테이블 건수 불변 확인.
- production 화면 360/390/430/768/1440px 가로 넘침 검사와 스크린샷 확인. Taste 홈 0 FAIL·1 WARN(데스크톱 첫 화면 아래에 이어지는 미션 카드), 관리자 대역 화면 0 FAIL·0 WARN. #16 모바일 비교 화면도 확인했다.
- 백엔드 JDK 21 Docker 환경에서 `test integrationTest bootJar` 성공: 단위 84/84, 통합 총 139개 중 138개 통과·옵트인 장애 리허설 1개 제외, 실패/오류 0개. 최초 실행은 docs 디렉터리를 마운트하지 않아 문항 파일 참조 검사 1개가 실패했으며, 저장소 전체를 마운트한 재실행으로 해소했다. 검사 종료 중 이미 폐기한 임시 DB/Redis에 대한 재접속 경고는 남았으며, 최종 Gradle 종료 코드는 0이다.

테스트 환경 준비는 PR 승인·운영 배포 검증을 의미하지 않는다. 미션 지급 정책·시상 변경에 대한 최종 결정과 실제 기기·DM·현장 부하는 별도 확인 대상이다.
