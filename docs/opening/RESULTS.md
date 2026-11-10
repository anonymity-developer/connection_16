# 오픈 준비 검증 결과 — 2026-11-09 KST

**현재 전체 상태 NOT_VERIFIED, 결정 NO_GO_PENDING_EVIDENCE.** 운영 설정이 잘못됐다고 단정하지 않으며, 실환경 증거/승인이 없으므로 자동 GO를 내리지 않는다. 실제 Work 결과 JSON을 ZIP logs/opening-work-result.json에 포함한다.

| 항목 | 결과 | 실제 실행·범위 |
| --- | --- | --- |
| 선행 7개 패치 재구성 | PASS | 원본86ad1dd→UI v5→후속→final→missions→awards→rehearsal→post-event, 독립 worktree 순차 적용 |
| frontend lint | PASS | npm --prefix frontend run lint |
| TypeScript/Vite build | PASS | npm --prefix frontend run build |
| 프론트·SSR·도구 단위 | PASS 77/77 | 기존66 재사용+신규 오픈 진단11. 현재 통합 상태 실제 실행 |
| 사후 분석 Python 계약 | PASS 6/6 | python3 scripts/post-event/test_activity_dataset.py -v. 기존 HMAC/시각/가상 pandas 계약 그대로 재사용 |
| 설정/시간/NFC/문항 검사 | PASS (fixture) | 10초·정책누락·활성미션·4개NFC·해시통계·문항선택지/축/유형·달력/마이크로초·민감출력거절·공개GET허용필드 검사 |
| 진단 오류 red/green | PASS | 원본9초/2월30일 진단 true → 수정 false. 로그 제공. 실제 서버 오류를 재현한 것은 아님 |
| API·메뉴·도구 선언 정합성 | PASS 25항목 (정적) | node scripts/readiness/check-opening-contracts.mjs. 경로 문자열/코드/파일 존재 검사이며 실제 API 호출 검증 아님 |
| Work 기본 자동 진단 | PASS (기능 검사) | 입력/네트워크 없이 JSON 생성, exit2와 NOT_VERIFIED·NO_GO_PENDING_EVIDENCE. 운영 준비 PASS 아님 |
| 패치 적용/역적용·보존 | PATCH_VERIFICATION.md | 최종 byte 비교와 이전 상태/원본 clean 검사 |
| Java21·Spring·MySQL/Redis·read-only SQL 실행 | 미검증 | 기존 환경 제약. 설치 반복/H2 대체/운영 연결 없음 |
| 실제 관리자 권한·동의·미션 지급·시상 정산 | 미검증 | 기존 Java/Testcontainers 회귀 재사용 대상으로 남김. Node의 거절 fixture를 서버 권한 PASS로 해석하지 않음 |
| 실제 브라우저·PowerShell·NFC/QR 실물 | 미검증 | 이전 Chromium 실행 파일 부재. 반복 설치/실기기 테스트 안 함 |
| MM 실제 로그인·OTP/DM·발신 승인 | 미검증 / NEEDS_APPROVAL | 외부 요청/비밀정보 조회 안 함. 과거 사용자 성공 보고를 이번 운영 환경 증거로 승계하지 않음 |

현재 오픈을 막을 수 있는 잔여 항목: 실제 일정/현재 정책·가입 전 안내/운영 설문·버전/네 태그 및 위치/승인된 발신 전용 계정/관리자·검수 인력/실제 5미션 리허설. 선택 운 점수 미설정은 필수 차단과 분리한다.

도구별 검사 증거·개선 방법은 JSON 각 check의 evidence/action에 포함한다. 운영 승인·실환경 증거를 설정 파일에 true로 적어 자동 GO로 바꾸는 우회 플래그는 없다. 실제 GO/NO-GO 체크는 [GO_NO_GO.md](GO_NO_GO.md), 패치 정합성·수정 결함은 [CONSISTENCY.md](CONSISTENCY.md)를 따른다.

주의: 기존 SSR 테스트의 병렬 Vite 서버에서 HMR 포트24678 사용 중 경고가 발생했다. 최종 재실행은 종료코드0·77/77 PASS로 확인했으며 출력 전체를 보존했다. 이 경고를 실제 브라우저/운영 서비스 오류로 판단하거나 해결됐다고 주장하지 않는다.
