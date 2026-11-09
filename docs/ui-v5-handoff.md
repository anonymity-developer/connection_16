# TAG ME UI v5 — 개발 완료·적용 대기

기준: `86ad1dd` + 1~4차 전체 패치. 2026-11-07 KST 검증. 기존 v4 작업 공간은 보존하고 별도 `work/ssafy-ui-v5`에서 작업했다. commit/push/PR/배포 없음.

## PC·모바일 개선 요약

| 영역 | 4차 화면에서 확인한 문제 | 5차 구현 |
| --- | --- | --- |
| PC 홈 | 큰 히어로와 아래로 밀린 미션 | 1100px 이상 수집판/CTA 2열, 그 이하는 1열, 히어로/간격 축소 |
| 모바일 홈 | 긴 로그인 카드와 반복 안내, 라벨 줄바꿈 | 로그인 CTA를 한 줄 동선으로 압축, 처음 참여/바로 아래 미션 링크 제거, S S A F Y 라벨과 정식 이름 접근성 라벨 |
| 진행/힌트 | 평면 글자와 흩어진 안내 | 공통 MissionProgress로 현황/네이티브 details 힌트 구분, 기존 5개 미션 상세 경로 재사용 |
| 글자 | 상세 보상/완성 화면의 평면 알파벳 | 동일 v4 WebP를 사용, 획득과 미획득 구분, 반짝임은 같은 모양 SVG로 표현 |
| 운영 | 평문 로그인, 좁은 필터 | 공통 서비스 쉘/EmptyState, 단정한 탭·배지·필터·상세 패널, 줄바꿈/넘침 처리 |
| 비로그인 | 큰 빈 안내 카드 | 동료/계정 아이콘 구분, 짧은 문구와 공통 카드·CTA |
| 푸터 | 중복 홈 링크 | 요청한 이벤트/기획·제작 크레딧 2줄, 모바일 고정 내비 안전 여백 |

수집판은 공식명을 임의 축약하지 않는다. 각 슬롯의 title/aria-label에 정식 영문명을 유지한다. 첫 번째 S와 두 번째 S는 mission code로 구별된다.

## 로직 보존

- 백엔드, API 클라이언트, 인증·세션·Mattermost·NFC 복귀·개인정보·DB 변경 없음.
- App의 운영자/완성 화면 진입 조건과 Operations의 권한 확인·요청·충돌 처리·감사 기록 로직 유지. Operations.tsx 변경은 배지 data-state 두 곳뿐.
- 상태는 기존 ScreenData와 서버 응답을 사용. 결과 미션의 PENDING은 진행 중, 소원/응원의 PENDING은 검수 중. 미확인/비로그인 진행 카드는 획득으로 표시하지 않음.
- 힌트는 기존 화면에 이미 있는 프로필·질문·연습·검수 안내만 재사용. 실제 위치/태그 URL/토큰/문제 정답/승인 보장 없음. details를 여는 동작은 API를 호출하거나 해금하지 않음.
- v4 이미지 5개/WinterDecor/Auth UI 원본 그대로. 별도 마스코트/패키지/애니메이션 없음. reduced-motion 유지.
- CSS 안전 영역은 body와 쉘에 중복 적용되지 않도록 모바일에서 쉘이 담당.

## 검증 결과

- PASS: frontend ESLint, TypeScript/Vite build, 단위 테스트 19개, git diff --check.
- PASS: 5개 미션 × LOCKED/UNLOCKED/PENDING/COMPLETED SSR 현황/힌트, 비로그인·서버 미확인 획득 오표시 방지.
- PASS: 증분/통합 binary patch 적용·파일 바이트 비교·역적용. 4차 및 기준 커밋 상태로 각각 복원 확인.
- 보조 앱 스타일 검사: 0 FAIL, 21 WARN (요청한 기존 그림자 20곳, OTP 시계 padStart 1곳). 랜딩 페이지용 기본 모드는 이 앱에 맞지 않아 앱 모드로 다시 실행함. 검사 규칙을 수정하거나 억제하지 않음.
- 미검증: 360/390/430px·PC 실렌더, Safari 키보드/포커스·실제 고정 내비 간섭, 관리자 로그인 후 실제 데이터, 실제 NFC/서버 인증.
- 브라우저 실행 시도: 설치된 Chrome가 `socket() failed: Operation not permitted`로 종료. 실행 시도를 화면 검증 PASS로 간주하지 않음.
- 새 `frontend/scripts/check-polish-ui.mjs`: production build의 360/390/430/1280px 홈·빈 화면·미션 상태/힌트·운영 권한 게이트를 루프백 GET fixture로 확인하는 로컬용 스크립트. 이 환경에서는 브라우저 제한으로 미실행. 기존 auth/NFC/operations 브라우저 계약 테스트도 미실행.

## 로컬 확인 순서

`frontend`에서 `npm.cmd run lint`, `npm.cmd run build`, `npm.cmd run test:unit` 실행 후 `node scripts/check-polish-ui.mjs`. Playwright Chromium이 없는 경우 로컬에서 `npx.cmd playwright install chromium` 후 실행 가능. 검사는 운영 서버에 연결하지 않으며 운영 POST/PATCH를 허용하지 않는다.

수동 확인: 360/390/430 및 PC 홈 → 로그인 전 동료/계정/admin → 기존 허가된 로컬 테스트 계정으로 각 미션과 운영 화면. 운영 기능 검증은 기존 `npm.cmd run test:operations-ui`, 인증/NFC는 `npm.cmd run test:auth-nfc-ui`를 사용한다. 실제 운영 데이터 변경 없이 검토한다.
