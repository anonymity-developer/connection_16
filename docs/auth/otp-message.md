# Mattermost OTP DM 표시 — 2026-11-09

Mattermost 공식 문서는 `**텍스트**` 굵게 표시와 Markdown 메시지 형식을 지원한다: https://docs.mattermost.com/end-user-guide/collaborate/format-messages

회원가입 제목은 **[TAG ME] 회원가입 인증 코드**. 목적 label을 그대로 사용하여 비밀번호 변경/재설정도 각 제목으로 구분한다. 인증번호는 빈 줄로 분리한 굵은 단독 줄이며 마커 안쪽에 공백/들여쓰기를 넣지 않는다. 줄 전체를 선택할 때 문장과 섞이지 않도록 한 구성이다. Mattermost의 실제 클립보드/모바일 선택 동작이나 실렌더링을 확인한 것은 아니다. 별도 복사 버튼은 추가하지 않는다.

본문은 `아래 인증번호를 복사해 입력해 주세요.` 다음 강조된 서버 코드, 아래에 유효 시간/공유 금지/미요청 시 무시 안내다. 문서·테스트 로그에는 실제 번호/DM 본문을 출력하지 않는다.

유효시간은 기존 AuthProperties.otpTtlSeconds()를 그대로 전달한다. 기본180초는 `3분(180초)`로 표시한다. 설정을 다른 값으로 변경하거나 TTL을 새로 계산하지 않는다. 테스트의 다른 TTL은 동적 표시 검사용으로 실제 정책 변경이 아니다.

변경은 MattermostClient의 message 문자열 생성 한 곳 → OtpMessage.format helper다. OTP 생성/6자리/검증/재발송·요청 한도/세션/MM 인증·API 호출·발신 계정/로그아웃 구조는 변경하지 않는다. 실제 MM/DM/사용자/DB를 사용하지 않았다.

## 검증

- PASS: 실제 OtpMessage.java + OtpMessageSmoke를 JDK17 compiler module로 컴파일·실행. 목적3종/별도 강조 줄/선행0/주의 문구/동적 값과 TTL. stdout은 성공 여부만 출력하며 코드·본문은 출력하지 않음.
- 작성 완료·미실행: OtpMessageTest JUnit2개. 실제 Purpose enum의 모든 용도와 동적 표시 검증. 실패해도 본문/코드를 덤프하지 않도록 boolean assertion만 사용.
- 미검증: JDK21/Spring 및 기존 MattermostSenderSessionTest/AuthPropertiesBindingTest 실행. 기존 인증 테스트 파일/expectation은 보존했다.
- 미검증: Mattermost 실렌더링·실제 복사 동작. 공식 Markdown 문서 확인은 실서버 렌더 PASS가 아니다.
- 프론트 수정 없음. 이번에 기존77개를 재실행했다고 보고하지 않는다.
