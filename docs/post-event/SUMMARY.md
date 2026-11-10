# TAG ME 사후 운영·분석 추출 준비 — 개발 완료 / 적용·통합 실행 대기

- `/admin?tab=results`와 관리자 results API에 현 cohort 참여 지표·5개 미션 현황·글자/검수/성향/TAP 분포·실제 원장 시간대·기존 잠정 시상 후보/제외 근거를 연결했다. 같은 REPEATABLE_READ 읽기뷰·고정 DB 관찰 시각을 사용한다. 시상 계산/보상/NFC/관계 가중치/디자인 자산 보존.
- 자료 지문·이전 조회 주요 수치 비교와 실제 CSV/JSON/Markdown 첨부 다운로드를 구현했다. 바뀐 자료는409 재조회, 최소 후보 P-ID만 포함하고 닉네임/실명/MM/원문/인증정보를 제외한다. CSV 수식/인용·캐시 금지·서버 DB 권한 재검증 포함.
- 알려진 내부 ID로 미션 상태/완료 근거/접수 시각만 보는 제한 조회를 추가했다. 개인별 활동 로그 반출 API/실 DB 추출 CLI는 없다. 새 로그 수집/DB 스키마 없음.
- 추가 요청을 반영해 **퍼널 대시보드·전환율·이탈·시각화·인사이트는 미구현**이다. 실제 DB 이벤트 정의·추출 SQL 설계·6컬럼 명세·기간별 HMAC 가명화 순수 계약·가상 fixture CSV·pandas 스키마/UTC 로딩 템플릿만 제공한다. source 시각 의미와 기록되지 않는 항목을 명시했다.
- 실제 Work: lint/build/Node66·Python6·Java 순수 export smoke PASS. Java21/Spring/MySQL/Redis/브라우저 렌더링은 미검증. 브라우저는 실행 파일 부재로 시작 실패했으며 반복 설치하지 않았다.
- 동점·중복1위·미처리 검수·미확정 정책을 보이지만 공식 수상 확정·추첨·지급·삭제는 제공하지 않는다. 보관/접근/백업/분석 반출 등 승인 목록과 행사 결과 보고 템플릿을 포함했다.

선행 적용 순서: final → missions → awards → rehearsal → **post-event-over-rehearsal.patch**. 실제 사용자 Windows worktree에 직접 적용하지 않았다. 독립된 경쟁 패치 없이 이번 증분 하나에 추가 분석 요청을 모두 통합했다. commit/push/PR/merge/배포 및 실제 데이터 접근 없음.
