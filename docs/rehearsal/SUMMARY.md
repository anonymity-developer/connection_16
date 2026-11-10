# 행사 직전 리허설 후속 — 적용 대기

최신 final → 미션 → 시상 패치를 별도 worktree에 재구성하고 새 시상 ZIP의 변경 파일 바이트를 확인했다. 현재 사용자 로컬 적용 여부를 가정하거나 Windows 폴더를 수정하지 않았다.

- 실제 수정은 REH-01 FAST TAP 과거 CANCELLED 응답의 slot 오표시 1건이다. 시작 경합 해결에 쓰던 기존 서버 재조회 함수를 취소에도 재사용한다. 기존 공식 1회/서버 시간·보상·NFC 정책은 유지한다.
- EventRehearsalIntegrationTest를 추가했다. MM/외부 provider만 mock, Spring HTTP/CSRF/Redis 세션과 실제 MySQL 트랜잭션/완료/검수/시상 계산은 실제 서비스다. 기존 기능별 테스트를 복제하지 않고 모듈 사이 연결을 검사한다. 가짜 정책/설문은 테스트 클래스의 전용 컨테이너에만 존재한다.
- 필수 동의·프로필·S 자동 지급 → 익명 pending/로그인 → 네 NFC → 설문/추천 → 같은/다른 계정 동시 소원 → 승인/수정 → 실제 5초+10초 공식 입력 → 응원 승인 및 마지막 완료 rollback/재시도 → 하트/댓글/시상·일반 계정 차단을 연결한다. 사전 등록과 마감 경계도 별도 실제 HTTP 케이스로 검사한다.
- opt-in 장애는 임시 Redis/MySQL 컨테이너 pause/unpause와 fail-closed/복구·기록 보존 검사다. 앱 프로세스 재시작·실기기/실제 MM 장애/접속 폭주 성능을 검증했다고 주장하지 않는다.
- PowerShell runner는 고정된 격리 테스트만 실행하며 실제 URL/DB target/비밀번호를 받지 않는다. 기본 실행은 프론트만, backend/browser/faults는 명시 선택이다. 미실행은 UNVERIFIED, fault는 JUnit 실제 실행 여부까지 검사한다. 기존 Compose/실사용 볼륨은 건드리지 않는다.
- 실제 Work lint/build/단위 60 PASS. 새 HTTP+DB/장애/브라우저·PowerShell은 실행하지 못해 미검증이다. [결과/재현](RESULTS.md), [Windows 명령](APPLY.md), [행사 대응](RUNBOOK.md), [실기기 체크](DEVICE_CHECKLIST.md)를 확인한다.

새 디자인·라이브러리·마이그레이션·행사 일정·동의문·시상 규칙은 추가하지 않았다. SHOW ME 자동 지급, SAY 파이팅!, 소원 검수/운 점수, 시상 잠정 후보와 관계 그래프를 보존했다. 실제 DB/MM/참가자/수상/DM 작업 및 commit/push/PR/merge/배포 없음.
