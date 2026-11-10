# 새 관리자 API · 화면

화면: `/admin?tab=results` → 기존 서버 운영 권한 gate 뒤에 표시. 기존 검수/콘텐츠/감사/시상 탭 유지.

| GET 경로 | 응답 / 접근 |
| --- | --- |
| /api/v1/admin/results | 참여 지표·미션·분포·시간대·4부문 잠정 후보·검토 근거·정책 대기. 현재 DB ADMIN 재확인 후 같은 REPEATABLE_READ에서 계산 |
| /api/v1/admin/results/export?format=csv\|json\|md&snapshot=자료지문 | 실제 UTF-8 첨부 자료. 최신 반환 자료와 지문 다르면409 RESULT_CHANGED. CSV는 BOM/CRLF/전 셀 인용·수식 방어. JSON·MD도 원문/닉네임/외부 ID 없음 |
| /api/v1/admin/results/participants/{내부번호}?purpose=RESULT_RECONCILIATION&after=0 | 현재 ACTIVE USER의 미션 상태·완료 근거와 접수 원장 최대50건, nextCursor. 알려진 내부 번호로 운영상 결과 대조만. 검색/목록/개인별 다운로드 없음 |

모든 새 응답은 no-store/Pragma no-cache/nosniff. 미인증401, 현재 DB 권한 없는 계정403; AuthPrincipal의 role 문자열만 신뢰하지 않음. 제한 조회 잘못된 목적/번호/커서는400, 조회 불가 cohort는404. 기존 전역 인증/CSRF 정책 완화 없음. 신규 행위 수집·감사 로그 추가·데이터 수정·공식 수상 확정 API 없음. 오류 메시지에 원문/비밀번호/토큰/SQL 파라미터를 추가하지 않는다.

기본 자료는 집계 및 관리자 검토용 최소 후보 내부 ID(`P-...`)만 포함한다. 실제 활동 로그 6컬럼 반출 API는 **없다**. HMAC 개인별 데이터셋 도구는 가상 fixture 전용이며 승인 후 활성화하는 우회 플래그도 없다.

자료는 현재 DB 스냅샷이며 영구 확정본을 저장하지 않는다. 관찰 시각·마감·범위·지문과 검토 대기 상태를 함께 표시한다. 이전 조회와 지문/주요 수치를 브라우저 메모리에서 비교하며 localStorage/외부 전송하지 않는다. 상세 검수 원문 열람은 기존 검수 화면에서 기존 권한으로만 수행한다.

시상 후보 계산은 기존 `OperationsRepository.awardFacts`와 `AwardCalculator.rank`를 그대로 재사용한다. 변경은 SQL의 여러 UTC_TIMESTAMP 비교를 고정 observedAt으로 묶는 overload이며 시상 규칙·동점·지급·관계 가중치 변경이 아니다. 이전 `/admin/awards`도 같은 고정 시각 경로를 사용한다. 기존 시상 회귀는 추가 통합 검증 대상이다.
