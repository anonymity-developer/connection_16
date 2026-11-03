# 페이즈 1 UI 검수

## 구현 범위와 디자인 결정

수집판, 미션 안내, 로그인/가입/비밀번호 재설정/변경, 대표 소원 입력과 완료 연출의 프론트 UI다. 서버 인증·태그 검증·검수·글자 지급은 구현하지 않았다. 운영 빌드는 로그인하지 않은 안내 상태만 제공하고, 가상의 닉네임/진행 상태는 개발 서버의 `?preview=locked|unlocked|pending|complete`에서만 불러온다.

Taste 2.1.5의 앱 절차와 `app-screens.md` 전체를 읽고 앱으로 draw했다. community/home/detail/settings, seed `99394808`, `accent-ladder` 방향을 선택했다. 어두운 대안보다 현장 낮 시간의 빠른 확인에 맞는 밝은 화면을 택했다. 별도 관리 화면이 아닌 세 개의 사용자 목적지이므로 상단 내비게이션과 모바일 하단 내비게이션을 사용한다.

초안의 문제는 코드 검토를 근거로 정리했다. 큰 홍보 제목/스티커/가짜 티켓 번호/바코드가 수집보다 먼저 보였고, 7~10px 보조 글자와 다섯 색이 읽기 순서를 흐렸다. 로컬 브라우저 실행이 막혀 **변경 전 스크린샷을 확보한 것은 아니다.**

수정 후의 화면별 첫 질문은 다음과 같다.

| 화면 | 먼저 답할 질문 | 주요 구성 |
| --- | --- | --- |
| 홈 | 어떤 글자를 모으고 다음에 무엇을 하나? | SSAFY 수집판, 두 S의 미션 이름, 다음 행동, 미션 목록 |
| 인증 | 어떤 계정 정보로 무슨 절차를 진행하나? | 목적별 제목, 화면 미리보기 안내, 입력/오류, 계정 안내 |
| 미션 | 참여 가능한가, 무엇을 해야 하나? | 미션/상태, 태그 안내 또는 입력, 획득 조건 |
| 탐색/내 정보 | 현재 무엇을 확인할 수 있나? | 준비 또는 로그인 안내, 돌아갈 경로 |

컬러는 라즈베리 한 계열의 작은 강조와 연한 면을 사용한다. 배경 `#fbf9fa`, 본문 `#202029`, 보조 `#655d65`, 강조 `#aa285b`, 연한 강조 `#fbf0f4`, 강조 텍스트 `#72203f`를 CSS 변수로 공유한다. 상태는 아이콘과 글자를 함께 쓴다. 제목 24~28px, 본문 14~16px, 보조 12~13px를 기준으로 한다. 수집 글자는 읽어야 할 수집 대상이므로 36~48px이며 숫자 KPI가 아니다.

영문은 DM Sans variable을 로컬 WOFF로 제공한다. 원본은 [Google Fonts DM Sans](https://github.com/google/fonts/tree/main/ofl/dmsans)의 `DMSans[opsz,wght].ttf`이며 fontTools로 형식만 변환했다. OFL 원문은 `frontend/public/fonts/DM-Sans-OFL.txt`에 보존했다. 한글은 OS의 Apple SD Gothic Neo/Noto Sans CJK KR/Malgun Gothic을 사용한다. CI에는 Noto CJK를 설치한다.

## 동작 수정

- 개발 상태 전환 링크의 명시적 `preview` 값을 현재 값으로 덮어쓰던 문제를 수정했다. 내부 이동에서는 상태를 유지하고 운영에서는 전달하지 않는다.
- 공개 홈에 실제 기록처럼 `0/5`를 표시하지 않는다. 로그인 후 확인 안내를 쓰고, 진행률은 개발 상태에서만 계산한다.
- 인증 오류 시 첫 오류 입력에 포커스한다. 인증 단계로 이동하면 하나의 숫자 입력에 포커스하며 앞자리 0을 문자열로 보존한다.
- 인증 단계에서 아이디를 바꿀 수 있다. 돌아갈 때 번호·비밀번호·확인을 지운다. 화면 이동 후 입력값을 복원하거나 저장하지 않는다.
- 로그인/가입/재설정/변경의 제목과 자동완성을 구분한다. 모든 입력 화면은 실제 전송이 없다는 안내를 제출 전에도 보여준다.
- 미션 상세에서 홈 메뉴가 활성화되고, 없는 주소·태그 검증 준비·로그인 필요 화면에도 한 개의 h1을 둔다.
- 버튼 44~48px 이상, 입력 16px, 모바일 safe-area와 reduced-motion을 반영한다. 소원 오류는 입력 포커스와 연결된 도움말을 제공한다.

## 검사와 재현

```bash
node scripts/verify-skills.mjs
node scripts/taste.mjs lint --app frontend/src
cd frontend
npm ci
npm run lint
npm run test:unit
npm run build
npx playwright install --with-deps chromium
npm run test:ui
```

`test:ui`는 빌드 산출물의 preview 서버(4173)와 별도 개발 서버(5174)를 띄우고 종료한다. 360/390/430/768/1440px에서 홈/로그인/가입/잠긴 소원/탐색 화면, 가로 넘침·한 개의 제목·영문 폰트 로드를 검사하고 스크린샷을 남긴다. 운영의 미리보기 배제/비밀번호 화면 차단, 입력값 전송·저장 없음, 내비게이션/뒤·앞 이동, 개발 상태 전환, `012345` 실제 클립보드 붙여넣기, 비밀번호 확인 오류, native dialog의 Tab/Escape/포커스 복귀, 소원 입력을 검사한다. 실제 인증·검수·저장 테스트가 아니다.

CI의 Frontend job은 위 검사를 실행하고 `frontend-ui-review` 아티팩트에 스크린샷과 `checks.json`을 남긴다. 별도로 운영 preview에 Taste inspect를 실행한다. 로컬 또는 CI에서 테스트가 성공했다는 사실과, 사람이 그 스크린샷을 실제로 검토했다는 사실을 구분한다.

### 로컬 결과

- ESLint, 운영 빌드, 단위 테스트 8개 성공.
- Taste 앱 정적 검사: `0 FAIL, 0 WARN`. 원본 스킬 파일 55개 무결성 성공.
- Taste inspect: 시스템 Chromium의 `socket() failed: Operation not permitted`로 실행 중단.
- Playwright CLI 0.1.23 설치/버전 조회는 성공했다. 런타임의 headless shell 다운로드는 손상된 ZIP 응답으로 실패했다. 설치 가능한 CLI와 실행 가능한 브라우저를 구분한다.
- 대체 브라우저에서도 로컬 개발 서버는 `ERR_CONNECTION_REFUSED`였다. 따라서 로컬의 운영 화면/입력 브라우저 검사를 통과했다고 기록하지 않는다.
- CI 실행 결과와 실제 화면 검토는 후속 확인이 필요하다. 이 검증이 끝나기 전에는 페이즈 1 전체 완료로 표시하지 않는다.

### 1차 CI와 화면 비평

[CI 41591922433](https://github.com/anonymity-developer/connection_16/actions/runs/41591922433)에서 운영 5개 폭의 배치/가로 넘침/폰트/제목, 운영 미리보기 배제/직접 경로 제한, 로그인 입력/표시/뒤·앞 이동/전송·저장 없음 검사는 성공했다. Backend/Docker도 성공했다. 개발 상태 검사에서는 `preview()`가 설정한 `NODE_ENV=production`을 같은 프로세스의 개발 서버가 물려받는 검사 도구 문제가 확인돼, 개발 서버 생성 전에 development를 명시했다. 실제 앱의 운영 보호를 해제한 것이 아니다.

아티팩트의 홈 390/1440, 로그인 390, 잠긴 소원 390 이미지를 직접 열어 확인했다. 가장 먼저 수정할 세 항목은 좁은 수집 칸의 한글 단어 중간 줄바꿈, 모바일 계정 안내 제목의 붙은 문장, 입력 경계선 대비였다. 수집 칸에는 두 S의 원래 이름과 소원/탭 게임/응원 짧은 라벨을 쓰고 전체 미션 이름은 목록/접근성 이름에 유지했다. 계정 안내의 명시적 줄바꿈을 유지하고 입력 경계선은 흰 배경 대비 3.28:1인 `#968b93`으로 조정했다. 보조 텍스트와 수집 글자 등 주요 텍스트 조합은 계산상 4.5:1 이상이다. 운영 스크린샷을 확보했으나 개발 동작과 Taste inspect는 재검사가 필요하다.

2차 CI에서 운영 화면과 개발 상태 전환/뒤·앞 이동, `012345` 클립보드 붙여넣기, 비밀번호 불일치 포커스가 실제 브라우저에서 성공했다. 팝업의 마지막 버튼에서 Tab을 누르면 브라우저 영역으로 포커스가 이동하는 현상을 검출했다. native dialog의 배경 비활성화에 더해 Tab/Shift+Tab 양끝을 명시적으로 순환시키고 두 방향을 회귀 검사한다.

3차 CI에서 브라우저 동작 검사 여섯 묶음이 모두 성공했다. 팝업 양방향 Tab/Escape/포커스 복귀와 소원 입력도 포함한다. Taste 정적 검사는 계속 0 FAIL/0 WARN이지만 inspector의 별도 Chrome 프로세스가 runner에서 종료됐다. 원본 스킬은 변경하지 않고 CI 전용 임시 launcher에 Playwright와 같은 `--no-sandbox` 실행 설정을 적용한다. 실제 앱/배포 설정을 변경하는 것이 아니며, 이 runner는 저장소의 로컬 미리보기만 검사한다.

완성 상태 캡처에서는 다음 행동이 태그 찾기로 남아 있는 점을 추가 발견했다. 전체 수집 후에는 완성 화면으로 이어지도록 수정하고 검수 대기 개수도 수집판 설명에 별도로 표시한다.

## 최종 검수 근거

[CI 41593244729](https://github.com/anonymity-developer/connection_16/actions/runs/41593244729)에서 Frontend/Backend/Docker가 모두 성공했다. `checks.json`의 브라우저 여섯 묶음은 모두 성공했고 pageErrors/writes는 빈 목록이었다. 운영 inspector는 0 FAIL/1 WARN(tiny-text)이었다. 보조 글자 경고는 무시하지 않고 수집 라벨/수집판 설명/준비 안내/미션 설명/상태 글자를 13px로 높였다. Taste 원본의 사전 정의된 세로 높이를 사용하는 1440/768/390px로 inspector를 실행하며, 별도 Playwright 검사가 360/390/430/768/1440px를 모두 확인한다. **최종 커밋 결과는 [PR #2 checks](https://github.com/anonymity-developer/connection_16/pull/2/checks)와 그 커밋의 frontend-ui-review가 기준이다.**

### 화면 비평 기록

앱 절차에 맞춰 수집·입력 화면을 평가했다. 마케팅 페이지의 사진/영상 수와 큰 홍보 제목 기준은 적용하지 않았다. 점수는 렌더를 본 주관적 평가이며 자동 통과 점수나 사용자 검증 결과가 아니다.

ROUND 1
Seen in one second: SSAFY 다섯 글자 수집판.
Idea (one sentence): 다섯 미션이 하나의 수집 대상에 모이고 다음 행동으로 이어진다.
Live thing: 각 미션 안내와 계정 입력으로 이동한다.
Layout words by section: home=collection+next / missions=list / auth=form+guide.
Scores: design 7 · usability 7 · creativity 7 · content 8 → overall 7.1.
Caps applied: 앱 기준으로 평가; 페이지용 미디어 수 규칙은 해당하지 않는다.
Top three problems:
1. home-390/360의 수집 라벨이 한글 단어 중간에서 잘림 → 짧은 소원/탭 게임/응원 라벨.
2. login-390 계정 안내 제목의 두 문장이 붙음 → 명시적 줄바꿈 유지.
3. login-390 입력 경계가 약함 → 경계선 대비 3.28:1로 조정.

ROUND 2
Seen in one second: 획득한 SSAFY 글자와 진행률.
Idea (one sentence): 잠금/대기/완료의 차이를 수집판과 미션에서 일관되게 읽는다.
Live thing: 상태 전환, OTP 붙여넣기와 대표 소원 확인 팝업.
Layout words by section: home=collection+next / missions=status-list / wish=input+conditions.
Scores: design 7.5 · usability 7 · creativity 7 · content 8 → overall 7.3.
Caps applied: 완료 화면의 다음 행동과 키보드 포커스를 보완할 필요가 있다.
Top three problems:
1. home-preview-complete-390의 완료 후 행동이 태그 안내로 남음 → 완성 화면으로 이동.
2. 실제 브라우저 검사에서 팝업 Tab이 브라우저 영역으로 빠짐 → 양방향 포커스 순환.
3. 검수 대기 개수가 다음 미션 안내에 가려짐 → 수집판 설명에 별도 표시.

ROUND 3
Seen in one second: 미션 목적이 구분된 수집판과 다음 행동.
Idea (one sentence): 같은 다섯 글자를 기준으로 잠금부터 완성까지 길을 잃지 않게 한다.
Live thing: 완료 화면 이동과 검증된 계정/소원 입력, 팝업의 닫기와 포커스 복귀.
Layout words by section: home=collection+next / missions=list / auth=form+guide / wish=state+input.
Scores: design 7.5 · usability 8 · creativity 7 · content 8 → overall 7.6.
Caps applied: 앱에 필요한 독자적인 수집 대상이 있으나, 전반 표현은 절제된 익숙한 앱 패턴이다.
Top three problems:
1. 모바일 미션 설명과 라벨의 12px 비중 → 13px로 확대 후 최종 CI 재검사.
2. 고정 하단 메뉴가 full-page 캡처의 중간에 보임 → 실제 390px viewport 캡처도 별도로 저장해 접힘 위치를 확인한다. 실제 스크롤 시 고정 메뉴는 화면 하단에 유지된다.
3. UI 미리보기와 실제 참여의 혼동 가능성 → 입력 전 안내와 개발 상태 바, 완료/소원 팝업에서 전송·저장 없음 유지.

실기기 NFC/QR와 실제 DM/서버 장애·대기 상태는 아직 검증 대상이 아니다. API 연결과 나머지 미션/프로필/탐색 화면은 다음 페이즈에서 구현한다.
