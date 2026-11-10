# 실행 결과 — 2026-11-08 KST

| 검사 | 결과 | 이번 실제 근거 |
| --- | --- | --- |
| 선행 통합 재구성 | PASS | 별도 work/post-event worktree, 원본86 → UI v5 → 후속 → final → mission → awards → rehearsal 순차 --check/적용 |
| frontend lint | PASS | npm --prefix frontend run lint |
| TypeScript/Vite build | PASS | npm --prefix frontend run build |
| 프론트·SSR·도구 단위 | PASS 66/66 | 기존60 재사용 + 신규6. 0명/미설정·잠정/동점/중복1위·HTML escape·자료지문/수치 비교·다운로드 권한오류/409/네트워크·제한 조회 목적/커서 |
| Python 분석 데이터 계약 | PASS 6/6 | python3 scripts/post-event/test_activity_dataset.py -v. HMAC 기간·키 분리/원장중복/금지필드·이벤트·시각·source·status/수식/실제 pandas 로딩 |
| 가상 CSV → pandas 로딩 | PASS | 내장 SYNTHETIC 5행, 2가명 ID. microsecond 유무 혼합 ISO8601 날짜 파싱 오류 재현 후 format='ISO8601' 및 UTC 검증으로 수정 |
| 실제 Java export 순수 smoke | PASS | JDK17 compiler module로 **실제** AwardModels/PostEventModels/PostEventExport + ExportSmoke 컴파일/실행. CSV 수식·인용/BOM/빈 Markdown. Spring/DB 실행 아님 |
| Java 소스 문법 parse | PASS (정적) | JDK17 JavacTask.parse 10개 변경 Java 파일. 심볼/타입 검사·Java21 빌드 아님 |
| Java21 / 전체 Spring / API 파일 다운로드 | 미검증 | Work JDK21 없음. 신규 PostEventIntegrationTest 9개 + 기존 전체 여정의 결과 통계 검증 코드 작성, 실제 실행하지 않음 |
| MySQL/Redis·SQL 실행·동시 스냅샷 | 미검증 | Docker 없음. Testcontainers 실제 MySQL/Redis 대상으로만 작성, H2 대체·운영 DB 접근 없음 |
| 브라우저 실렌더 | 미검증 (환경 시작 실패) | check-post-event-ui.mjs 실행 시 Chromium executable 없음. 설치 반복 안 함. 6폭/GET-only/권한 gate/CSV/409/권한 회수 fixture 코드 작성 |
| 실제 Windows/iOS/Android·시각 검수 | 미검증 | 사용자 실행 요청하거나 기다리지 않음 |
| 패치 적용·역적용 | ZIP의 PATCH_VERIFICATION.md 참조 | 선행 통합 기준과 최종 파일 바이트 비교, 새 증분 역적용과 원본 clean 검사 |

PASS는 이번 실행 근거만 기록한다. 이전 60개 결과를 그대로 승계하지 않고 신규 통합에서 재실행했다. 실제 참여자/운영 DB/MM/수상 확정·발표·추첨·삭제·외부 전송은 실행하지 않았다. 새 제품 기능의 API/DB 통합 PASS를 주장하지 않는다.

## 새 격리 DB 회귀와 기존 재사용

- PostEventIntegrationTest: 0명·미설정; 일부/프로필/서버 지급; 공개 철회·탈퇴; 숨김·미처리 수정본/하트 취소; 현재 DB 권한/제한 목적/미인증 GET; 같은 RR 중 별도 커밋 유입; 실제 스키마 추출 SQL; 실제 HTTP CSV/JSON/MD 첨부·캐시/403/409; 미설정/잘못된 설문 및 TAP REVIEW_REQUIRED.
- EventRehearsalIntegrationTest 기존 가입→프로필→네 NFC→설문→소원 검수→실제5+10초게임→응원/Y→rollback/재시도→전체완성→시상 여정에 post-event 후보8행·전체완성2명·하트1·공개타인댓글1·상호방문0·일반계정403 검증만 추가했다. 전체 여정 복제 안 함.
- 기존 OperationsIntegrationTest의 시상 동점·복수1위·승인 지연·댓글 중복/자기댓글/숨김·하트 취소·근거 이상·snapshot 동시성 회귀를 FullRegression으로 함께 수행하도록 runner에 연결했다.
- 새 Java CSV JUnit 회귀도 작성했다. 전체 Spring 컴파일/실행이 필요한 테스트는 미검증이다.

## 미확정·승인 필요

중복 수상/배분, 랜덤상 적격/인원, 남은 검수 처리, 공식 확정·정정·발표, 개인정보 보관·삭제·접근/백업, 개인별 로그 반출·HMAC 키 관리. 운영 정책을 기본값으로 채우지 않고 화면에 승인 대기로 표시한다. 기존 시상 안내의 '1인1수상' 단정 문구만 현재 미확정 요청에 맞춰 바꿨고 계산은 그대로다.
