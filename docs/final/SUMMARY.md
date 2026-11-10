# TAG ME 최종 통합 — 개발 완료·적용 대기

> 후속 미션 기획 변경은 [최신 적용 가이드](../missions-final/APPLY.md)와 [구현 요약](../missions-final/SUMMARY.md)을 우선한다. 이 문서의 실행 결과는 당시 이력이다.

2026-11-08 KST / 기준 HEAD 86ad1dd. 사용자 기준 상태는 UI 1~5차 + v5 후속 적용, 운영 준비/QA 미적용이다. 실제 사용자 폴더에 접근하거나 수정하지 않았다.

## 통합한 것

- 첨부 UI v5 ZIP은 후속 이전 버전이다. 이전 대화에서 실제 내려받은 TAGME_ui_v5_followup_86ad1dd.zip의 증분 패치와 대조해 사용자 현재 상태를 재현했다. v5-only ZIP을 현재 폴더에 다시 덮어쓰지 않는다.
- 운영 준비 패치 → 주 QA 패치 → qa-over-readiness 선택 설정 정정을 순서대로 적용·확인했다. 새 패치는 이를 한 번씩만 포함하며, 별도 구 ZIP 적용을 요구하지 않는다.
- QA의 FAST TAP 취소 응답 재조회와 SHOW ME 최종 showMeEligible 검증을 재사용했다. 중복 구현하지 않았다. FAST TAP은 React busy 갱신 전의 동일 tick 중복 시작을 막는 동기 gate도 추가했다. 실패 후 명시적 재시도는 가능하며 서버의 공식 기회 수·슬롯·보상 판정은 변경하지 않았다.
- 시작 키 생성도 기존 try/finally 안으로 이동해 생성 실패가 busy 해제 경로를 건너뛰지 않게 했다. 사용자/운영 서버에서 실제 오류를 재현했다고 주장하지 않는다.

## UI 보완

- 홈 큰 우측 안내 카드를 제거하고 수집판 전체 폭 사용. 제목/서브카피 유지, 위에 엘리베이터 대기라는 기획 의도 한 줄만 배치. 전체 완료 후 완성 기록 링크는 수집판 아래 유지.
- 5개 카드의 반복 CTA 텍스트 삭제. 카드 전체를 명확한 이름의 키보드 접근 가능한 상세 링크로 유지. 실제 서버 상태가 없는 경우 진행 배지를 표시하지 않음.
- 현황·힌트 카드는 지도/나침반 SVG와 선명한 블루로 구분. ‘다섯 미션’ 안내로 여섯 번째 미션처럼 보이지 않게 함. /missions 경로 유지.
- 검수 승인 후 글자 안내는 MAKE A WISH/SAY SOMETHING 카드에만 표시. 하단 중복 안내 제거.
- 잠긴 미션의 큰 공통 안내를 작은 NFC·QR 설명/필요 시 로그인 링크로 축소. 미션 상태·실제 본문이 우선이며 진행 중 본문과 중복되는 상태 박스 제거.
- 힌트는 미션별 수행 안내를 유지하고 공통 장문을 제거. 기본 위치는 ‘위치는 현장 안내를 확인해 주세요.’ 기존 Mission.hint가 실제 다른 문구로 설정되면 그대로 보여줌. 임의 층/위치 배정 없음. 현재 UI 카탈로그 hint와 DB hint가 자동 실시간 동기화되는 새 기능을 만들지는 않았다.
- v5 캐릭터 이미지 5개·겨울 효과·푸터 크레딧·reduced-motion·기존 인증/모달 동작 보존. 새 패키지/이미지 생성/공식 브랜드 자산 추가 없음.

## 시상 저장소 조사 (미확인 산출물과 구분)

GitHub에서 직접 확인:
- feat/network-graph HEAD = 86ad1dd87a64a1879766dc7682ff8eafc676e68d.
- f77aba1c3e9944482fb805269d8a67c988456662는 86ad1dd의 조상. 사이 5개 커밋은 세션 4h/NFC, MM 세션 인증, SHOW ME 소개·관심사, 인증 merge, 검증 문서다. 시상 기능 변경은 없음.
- f77aba1의 전체 Git tree(truncated=false)에 award/prize/ranking 구현 경로 없음. 현재 86ad1dd의 운영 API·스키마에도 시상 후보/확정/지급 구현이 확인되지 않음. f77aba1의 docs/operations-admin.md는 게임 검토·시상 정산을 후속으로 명시.
- 별도 TAGME_awards_candidates_f77aba1.zip은 제공되지 않았다. 당시 **기준** 커밋 f77aba1 자체를 시상 구현이라고 오인하지 않았다. 저장소에 없다는 사실로 미제공 ZIP 안의 구현까지 없다고 단정하지 않는다.
- 결론: **현재 확인한 저장소에서는 시상 후보 기능 미확인, 별도 ZIP 재사용·통합하지 않음.** ZIP/패치 또는 실제 구현 브랜치/SHA가 확보되면 해당 변경 파일과 시상 규칙 테스트를 대조해야 한다. 이번에 새 시상 알고리즘을 만들지 않았다.

근거 링크:
- https://github.com/anonymity-developer/connection_16/compare/f77aba1...86ad1dd
- https://github.com/anonymity-developer/connection_16/blob/f77aba1/docs/operations-admin.md
- https://github.com/anonymity-developer/connection_16/tree/feat/network-graph/backend/src/main/java/com/tagme

## 남은 고위험 확인

JDK21/Docker의 Java 컴파일·실 MySQL/Redis 동시성, 브라우저 계약/실제 Safari·Android·NFC, 운영 확정 일정/현재 정책·가입 전 안내, 승인된 발신 계정 운영, 시상 산출물 대조. 이전 실제 MM 성공·자격증명 제거·인증 비활성화와 로컬 21/21은 사용자 보고이며 이번 Work 결과로 승계하지 않는다.

실제 MM/개인정보/운영 DB 접근·쓰기·초기화, 자격증명 읽기/출력, commit/push/PR/merge/배포 없음.

## 추가 요청: 수집판 말풍선

기존 다섯 에셋을 유지하고 수집판 위에 정적인 공통 말풍선 `나를 찾아봐!` 하나만 추가했다. 슬롯과 분리된 행에 배치해 좁은 화면에서도 캐릭터/상태를 가리지 않는다. 미획득·진행·검수 대기에는 유지, 서버 확인된 다섯 글자 완료 시 제거한다. 기록 미확인 상태는 회색 초대 표현이며 미참여/획득으로 단정하지 않는다. 말풍선은 장식이고 기존 링크의 미션명·상태 접근성 이름/잠금 표시는 유지한다. 새 애니메이션·패키지는 없다.
