# 2차 SSAFY 스타일 사용자 UI - 개발 완료·적용 대기

기준: `feat/network-graph` / `86ad1dd87a64a1879766dc7682ff8eafc676e68d`.
작업 공간: 기존 작업과 별도 worktree, 로컬 `work/ssafy-ui-expansion`.
원격 반영·commit·push·PR·merge·배포·실서버 접속은 하지 않았다.

## 이전 패치 포함

`TAGME_ssafy_auth_ui_86ad1dd.zip`의 Git 패치를 새 worktree에 먼저 적용했다. 이전 ZIP은 변경하지 않았다. 이전 Auth/Profile/Tag/account.css/기존 추가 테스트/검증 문서는 바이트 비교로 동일함을 확인했고 progress에는 이번 상태만 추가한다. 통합 패치는 아직 1차 패치를 적용하지 않은 `86ad1dd`에서 사용한다. 1차만 적용한 상태라면 확장 전용 패치를 사용한다. 둘을 연속 적용하지 않는다.

## 구현 범위

| 화면 | 개선 |
| --- | --- |
| Home | 블루·화이트 수집판/다음 행동 카드, 미션 바로가기, 서버 기록 확인 중 안내, 미션 상태별 배지 |
| Mission | 참여 본문/보상·해금 가이드 카드, 모바일 버튼·여백, SHOW ME 안내의 소개·관심사 조건 명시 |
| Discovery | 동료 카드 2열/모바일 1열, 검색 폼 카드, 로딩/빈 상태/오류·재시도 구분, 관심사 없음 안내 |
| DiscoveryGraph | 같은 팔레트의 노드/목록, 관계 설명 펼치기, 기존 그래프/목록 대체·최대 8명·키보드 링크 유지 |
| Account | 프로필/태그/비밀번호 버튼 묶음, 기존 서버 수집판의 글자 수, 수집판 이동 카드 |
| PublicProfile | 상세 정보 카드, 읽기 쉬운 소개/관심사/링크, 빈 관심사 안내, 기존 공개 범위 유지 |
| 공통 Layout | 사용자 화면에만 스타일 범위 부여. `/admin`은 확장 팔레트·레이아웃에서 제외 |

공식 SSAFY 로고/정확한 색상 자료가 제공되지 않았으므로 공식 자산을 새로 만들거나 공식 값으로 표시하지 않는다. 1차 작업의 TAG ME 파란 팔레트와 기존 자가 호스팅 폰트를 재사용했다. 새 패키지/애니메이션 없음. 카드에 선택한 소원·응원 템플릿의 원래 색상 표현은 보존한다.

기존 framed-sheet 방향(seed 1340789336)을 확장했고 재추첨하지 않았다. 사용자 상태/주 행동이 가장 먼저 읽히도록 수집판과 다음 행동을 배치했다. 관리자는 기존 모습으로 유지했다. 브라우저 시각 검증을 마친 디자인 승인본으로 간주하지 않는다.

## 기능 영향과 검증

서버의 `IN_PROGRESS`가 기존 App에서 `PENDING` 표시 상태로 전달되는 경로는 변경하지 않았다. 홈·미션의 **표현만** 소원·응원은 검수 중, 성향·게임은 진행 중으로 구분한다. 수집판 접근성 이름도 같은 문구를 사용한다. 지급 완료는 기존 `COMPLETED`일 때만 표시한다. 순서·점수·제한·보상·인증/세션·NFC 복귀·검색/정렬/페이지 조회·프로필 상세 열기/방문 기록·공개 범위는 변경하지 않았다.

| 검사 | 이번 결과 |
| --- | --- |
| frontend lint / build | PASS |
| 단위 검사 | PASS 15개: 기존 13개 + 실제 배지 SSR 검사 2개 |
| 확장 UI 정적 디자인 검사 | PASS 0 FAIL / 0 WARN |
| diff 공백 검사 / 패치 정·역방향 검증 | PASS |
| 이전 전달 파일 보존 | PASS: progress 추가 기록 외 이전 전달 소스 동일 |
| 변경 금지 범위 소스 비교 | PASS: backend/infra/Operations/auth/participation/results/content/navigation 변경 없음 |
| 브라우저 동작·화면·오버플로·포커스·대비 실측 | 미실행: Chromium/Headless Shell 없음, 다운로드 zip 손상 오류. 원격 브라우저도 localhost 접속 거부 |
| 실제 모바일/NFC/서버 연동 | 미실행. 실사용자/서버 데이터에 접근하지 않음 |

SSR 배지 검사는 브라우저 검사가 아니다. UI 캡처를 대신하는 이미지를 만들거나 모의 화면을 실제 캡처로 제공하지 않았다. `TAGME_UI_review_onepage.pdf`는 개선 요약이며 실제 화면 캡처가 아니다.

## Windows 검증

패치 적용·되돌리기는 전달 ZIP의 `APPLY.md`를 따른다. 승인 전에는 별도 검토 브랜치에서만 실행한다.

```powershell
Set-Location 'C:\Users\amy\SSAFY_connection16\connection_16\frontend'
npm ci
npm run lint
npm run build
npm run test:unit
# 필요한 경우에만 브라우저 설치
npx playwright install chromium
npm run test:ui
npm run test:auth-ui
npm run test:auth-nfc-ui
npm run test:participation-ui
npm run test:results-ui
npm run test:content-ui
npm run test:reactions-ui
npm run test:discovery-ui
npm run test:operations-ui
```

브라우저 스크립트는 테스트 전용 API 대역을 사용한다. 실행 전 실제 MM 정보가 필요하지 않다. 운영 결과나 실기기 검사를 대신하지 않는다. 검사 시 캡처 파일은 `output/playwright/`에 생성된다. 새로 추가한 SSR 단위 검사는 Vite로 실제 배지 컴포넌트를 메모리에서 로드하며 API/계정/글자 기록을 만들지 않는다.

## 검토 후 남은 일

- 360/390/430px, 768px, 1440px에서 캡처·가로 스크롤·키보드 포커스·44px 터치 영역을 실측한다.
- 홈 → 미션, 연습/공식 도전, 제출/검수/완료, 탐색 검색/다음 페이지/뒤로가기, 그래프 노드/목록 이동을 검증한다.
- 실제 MM/모바일/NFC는 1차 검증 절차에 따라 별도 수행한다.
- 지원 언니 검토 후 사용자가 적용 여부를 결정한다. 이번 산출물의 최종 상태는 **개발 완료·적용 대기**다.
