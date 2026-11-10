# TAG ME 실제 운영 준비 및 로컬 검증

> 이 문서는 이전 운영 준비 단계의 근거와 실행 이력을 보존한다. 최종 통합의 최신 결과는 [최종 검증](../final/VERIFICATION.md)을 따른다. 선택 운 점수는 필수가 아니다.

기준 `86ad1dd` / 2026-11-07 KST. 이번 변경은 문서·오프라인/읽기 전용 도구·테스트뿐이다. 애플리케이션, UI 1~5차, 인증/NFC/보상/검수 규칙, DDL/API는 변경하지 않는다. 5차 후속 UI 패치도 포함하지 않는다.
사용자가 보고한 사실: 로컬 Docker 전체 Healthy, 실제 Mattermost OTP DM 및 TAG ME 가입·로그인 성공. 이번 Work에서 재실행한 결과가 아니다. 사용자 DB에는 접속하지 않았으므로 실제 행 존재/부재는 아래 읽기 전용 SQL로 확인해야 한다.

## 1. 장애 원인과 우선순위

| 우선 | 발견/분류 | 코드 근거·조치 |
| --- | --- | --- |
| P0 | 행사 미설정 | ParticipationRepository.event는 event_settings id=1이 없으면 NOT_CONFIGURED. requireOpen은 503 EVENT_NOT_CONFIGURED. Healthy는 DB/Redis 연결 상태이며 운영 설정 준비를 보장하지 않음 |
| P0 | 현재 정책 누락 | policyState는 현재 정책 두 종류가 연결되어야 ready=true. Profile은 consents.ready=false이면 폼 대신 대기 안내. save는 POLICIES_NOT_CONFIGURED/CONSENT_REQUIRED/POLICY_CHANGED로 차단 |
| P0 | 가입 전 개인정보 안내·수락 경로 없음 | AuthController.SignupRequest에는 challengeId/code/password만 존재. AuthService.signup은 프로필 동의 전에 계정/가져온 신원 저장. /policies는 인증 후 본인 기준 endpoint. 프론트 체크 하나 추가로 해결할 수 없음. 공개 전 운영자 승인과 서버 계약 설계 필요 |
| P0 | 발신 개인 계정 본인 인증 제한 | MattermostClient.sendCode의 target.id == sender.id 비교로 DM 전에 503. 그 계정의 가입/비밀번호 변경·재설정 OTP가 막힘. 코드의 명시적 제한이며 임의 해제하지 않음 |
| P1 | 빈 정책 본문도 준비된 것으로 판단 가능 | DDL은 NOT NULL이나 비공백 제약이 없음. policyState.ready는 건수만 검사. 검사 도구는 빈 제목/본문을 실패 처리. 실제 문구·버전 승인 없이는 게시 금지 |
| P1 | 미션 추가 설정 필요 | current_sync_questionnaire와 유효한 definition_json, 선택 운 점수 활성화 시 content_settings id=1, 활성 미션/태그, 검수 처리 경로와 운영자 권한 확인 |
| P1 | 보관/철회 운영 절차 미완 | 보관기간 자동 삭제 작업·사용자 철회 API/UI가 있다고 단정할 수 없음. 저장소에 withdraw/hide helper가 있어도 현재 컨트롤러 공개 기능과 다름. 담당자·요청 접수·처리·감사 절차 확정 필요 |
| P2 | 외부 실패 관측 구분 | MM 401/403/429/타임아웃은 공통 AUTH_UNAVAILABLE. logout은 finally에서 시도하지만 실패 시 서버 세션 폐기를 보장하지 못함. 원문 자격/OTP를 로깅하지 않는 운영 지표 설계 검토 |

정책 누락 안내와 이벤트 미설정은 별개다. 정책을 채워 폼이 보여도 일정이 없으면 저장은 서버에서 거절된다. 반대로 일정만 채워도 정책 없이는 저장 불가.

## 2. 실제 DDL: 필수값

원문: backend/src/main/resources/db/migration/V4__add_participation_policies_and_evidence.sql.

- event_settings: id TINYINT PK, id=1 제한. opens_at/submit_closes_at/moderation_closes_at DATETIME(6) NOT NULL. opens_at < submit_closes_at <= moderation_closes_at. game_duration INT DEFAULT 10 >0, profile_preparation_enabled BOOLEAN DEFAULT FALSE, settings_version BIGINT DEFAULT 1 >0.
- consent_policies: (policy_type VARCHAR(30), policy_version VARCHAR(64) ASCII binary) 복합 PK. type은 PARTICIPATION/PROFILE_PUBLICATION. title VARCHAR(200), body TEXT 모두 NOT NULL. 서버 Acceptance 버전은 `[A-Za-z0-9._-]{1,64}`로 더 엄격하므로 해당 형식 사용.
- current_consent_policies: policy_type PK, policy_version NOT NULL, 두 컬럼 FK가 실제 정책 버전을 참조. 각 종류에 현재 버전 1개씩 필요. 과거 정책을 UPDATE로 덮어쓰기보다 새 불변 버전과 현재 포인터의 승인된 전환 계획 필요.
- user_consents: (user, type, version) UNIQUE, agreed_at/withdrawn_at. user_consent_events는 AGREED/WITHDRAWN 이력. 새 현재 버전으로 전환하면 기존 동의가 자동으로 최신 동의가 되지 않음.
- BEFORE_OPEN에서 profile_preparation_enabled=true면 **프로필 저장만** 허용 가능. 현장 미션 해금/실제 참여는 OPEN 필요. CLOSED에서는 사전 준비 옵션으로 우회하지 않음.
- 서버/DB는 UTC 사용. DATETIME 자체에는 시간대가 없으므로 승인된 한국 시간 일정을 명시적으로 UTC로 변환하고 조회 시 UTC로 비교.

SHOW ME는 프로필·현재 두 정책 동의·공개 상태, 한 줄 소개·관심사 1개 이상·행사 OPEN 조건을 만족하면 프로필 저장 또는 인증된 재확인 요청에서 자동 지급된다. NFC는 나머지 네 미션에만 필요하다. 사전 등록은 기존 설정이 허용할 때만 가능하며 행사 시작 후 접속 시 실제 처리 시각으로 지급한다. [최신 기획 반영](../missions-final/SUMMARY.md)을 함께 확인한다.

## 3. 운영자 확정 체크리스트 (아직 승인된 정책 문구 아님)

- [ ] 실제 운영 주체·개인정보 담당자·문의/철회 접수 채널과 응답/처리 기준
- [ ] 계정 인증과 행사 참여의 수집 목적, 필수/선택 항목 및 거부 시 영향
- [ ] 가입 전 MM 조회, 가입 시 이름/지역/반 가져오기, 재로그인 시 갱신의 안내 시점
- [ ] 계정/비밀번호 해시/프로필/동의 이력/접수·검수·감사 로그/방문·하트 관계별 보관기간과 종료 후 처리
- [ ] 백업·서버 로그·운영자 다운로드까지 포함한 접근/보관/삭제 책임
- [ ] 공개 대상(인증된 참가자 등), 목록과 상세 공개 항목, 익명 작성과 공개 이름의 차이
- [ ] 외부 링크·MM 사진의 서버 경유 방식, 사진 원본을 DB에 보관하지 않는 현재 구현 설명
- [ ] MM·외부 검수 제공자에 전송하는 항목과 접근 권한, 사용 여부와 필요한 별도 안내 검토
- [ ] 공개 동의 철회 후 공개 차단/재노출 방지, 기존 완료·시상·감사 기록 처리 기준
- [ ] 행사 시작/제출 마감/검수 마감, 프로필 사전 준비 허용 여부
- [ ] SYNC 문항·유형/버전, 소원 운 점수·구역, FAST TAP 공식 도전 규칙의 운영 승인
- [ ] 실제 운영자 계정·권한 검증과 수동 검수 담당/근무 시간·조치 사유 기록

이는 코드와 운영 준비 항목의 검토표이며 법적 적합성 판정이나 승인된 약관이 아니다. 운영자/개인정보 담당자의 검토를 받아야 한다. 임의의 ‘행사 종료 후 N일 삭제’ 문구를 게시하지 않는다.

## 4. 가입 전/후 정보 처리와 동의 UX 제안 (미구현·승인 대기)

| 단계 | 현재 처리 |
| --- | --- |
| OTP 요청 전 입력 | 브라우저에서 username 입력. 요청 시 서버로 전송 |
| OTP 요청 | MM 사용자/채널 조회. MM 신원은 처리 중 메모리. Redis에 challenge 외부ID·기존 userID·purpose·credential version·binding 및 HMAC 코드 proof, 제한 키 저장. 사용자명/IP는 제한 키 생성에 HMAC 사용. 신규 SQL users/credentials 생성은 아직 하지 않음. DM에는 숫자 코드가 포함됨 |
| OTP 검증/가입 | MM ID/정식 username 재검증, 이름·지역·반·사진 갱신 버전·동기화 시각 저장, TAG ME 비밀번호 PBKDF2 해시와 버전/시각 저장, last_login_at 및 Redis 세션. 비밀번호 원문/OTP를 사용자 테이블에 저장하지 않음 |
| 프로필/동의 | 닉네임, 선택 소개/관심사/HTTPS 링크. 두 현재 정책 동의 저장·이력 후 공개 프로필 저장. 현재 화면은 한 체크로 두 종류를 함께 전송 |
| 로그인 | 비밀번호 검증 후 MM 이름·지역·반·사진 버전/last_login_at 재동기화. 계정 데이터 처리와 공개 동의는 동일한 시점이 아님 |
| 사진 조회 | DB는 MM 사진 버전 메타데이터. 이미지 바이트는 서버가 MM에서 가져와 검증한 PNG를 전달. 실제 네트워크·브라우저 캐시의 보관까지 무저장이라고 단정하지 않음 |

기본 인증 설정은 OTP 180초·재발송 60초·최대 검증 시도 5회, Redis 세션 4시간이다. 실제 활성 설정은 운영자가 비공개로 확인해야 한다. Redis TTL이 있다는 사실은 MySQL의 계정·동의·감사 데이터 보관기간이 확정됐다는 의미가 아니다.

계정 생성 후의 프로필 공개 동의는 이미 일어난 계정 수집을 소급 안내/수락하는 대체물이 아니다.

제안:
1. **첫 OTP 요청 전** 계정 인증 목적·MM 가져오기 항목·담당자·보관/철회 방법·필수 여부에 대한 승인된 안내를 공개 endpoint로 제공. 현 /policies는 비로그인 공개 안내로 사용할 수 없음.
2. 운영자가 정한 적절한 처리 근거/동의 방식에 따라 계정 단계의 버전/수락 증빙을 서버에서 검증. 필요 시 OTP 요청 시점과 계정 생성 트랜잭션에서 최신 버전 확인·저장. 체크박스를 프론트에만 추가하거나 OTP proof를 동의로 간주하지 않음.
3. 프로필 단계의 PARTICIPATION/PROFILE_PUBLICATION과 계정 단계 안내는 역할을 분리. 계정 약관 추가를 기존 두 type에 임의로 끼워 넣으면 DB CHECK/서버 enum/required count와 충돌하므로 별도 승인된 API·데이터 설계가 필요.
4. 참가자에게 목록/상세의 공개 범위를 명확히 보여주고, 필요에 따라 두 동의를 별도 선택하도록 설계 검토. 필수/선택 여부와 철회 후 영향을 운영자가 먼저 결정.
5. 새/변경된 정책을 미수락하거나 이전 버전일 때 서버 저장 거절, 사용자에게 재확인 UI. 운영 승인 전 실제 문구나 새로운 인증 계약은 이번 패치에 넣지 않음.

## 5. 승인된 Mattermost 발신 계정

- 개인 계정 대신 운영 승인된 전용 계정 확보. 명의/담당/접근자/만료/폐기 계획, 해당 서버의 계정 정책·MFA/SSO 호환 여부를 담당자에게 확인.
- 현재 구현이 사용하는 API: 계정 로그인(계정 방식), users/me, users/username/{name}, users/{id}, 지정 채널 member 조회, 선택한 team/channel name 해석, channels/direct 생성, posts 전송, users/{id}/image, logout(임시 세션 방식). 필요한 채널 조회·DM 권한을 실제 승인 환경에서 검증해야 함. 관리자 전체 권한이 반드시 필요하다고 단정하지 않음.
- AUTH_ENABLED=true 시 PAT **또는** login-id/login-password 한 방식만. 채널 ID **또는** team/channel 이름 쌍 한 방식만. 빈 값/혼용은 시작 단계에서 거부. 기존 Compose .env.example의 변수명 그대로 사용.
- 대상자는 활성 비봇 사용자이며 지정 채널 멤버여야 함. 발신자도 필요한 채널의 조회 권한이 있어야 함. username/이름만으로 소속을 인정하지 않음.
- 발신자 본인은 DM OTP 발송 불가. 신규 가입뿐 아니라 재설정/변경에도 영향. 일반 로그인의 비밀번호 검증이 self-DM을 요구하는 것은 아님. 운영용 계정을 참가자 계정으로 쓰지 않는 구조가 단순함. 개인 계정 교대/인증 우회/PAT 자동 fallback을 임의로 적용하지 않음.
- 계정 방식은 byUsername/byId/photo/sendCode **작업마다** 임시 세션 로그인과 logout을 수행하므로 한 가입 흐름에 여러 세션이 생길 수 있음. finally 정리 시도는 있으나 logout 네트워크 장애 시 MM 서버 폐기 보장은 없음. 개인 MM 사용과 충돌하는 현상은 이번 코드만으로 원인을 확정할 수 없으며 전용 계정과 서버 세션 정책 확인 필요.
- 비밀번호/PAT/HMAC/session/OTP를 로그/명령 인자/ZIP/문서에 넣지 않음. 실제 .env를 출력하거나 `docker compose config` 전체를 공유하지 않음. 재현 시 계정 이름·토큰 대신 비식별 오류 코드와 시각만 기록.

## 6. 로컬 전체 테스트 순서와 완료 기준

가장 안전한 자동 검증: 기존 Testcontainers MySQL 8.4/Redis 7.4 + @MockitoBean MattermostClient / MockRestServiceServer. 실제 OTP 채널/DM은 호출하지 않는다. 각 테스트의 DELETE/Redis flush는 전용 임시 컨테이너 내부에만 존재한다. 현재 compose DB나 기존 테스트 계정에 해당 초기화 코드를 실행하면 안 된다.

| 순서 | 확인 | 재사용할 검사 / 다음 단계 기준 |
| --- | --- | --- |
| 0 | 일정/현재 정책/기타 설정 | read-only.sql, audit.mjs. 준비 상태와 단순 Healthy 구분 |
| 1 | OTP→가입→로그인 | AuthIntegrationTest, MattermostSenderSessionTest. 선행0·purpose·one-time·CSRF, 잘못된 코드/비밀번호 실패. 실제 MM 성공은 사용자 보고만 별도 기록 |
| 2 | 세션 | AuthIntegrationTest. 쿠키 회전/이전 세션 차단. Redis 설정 4h 유지, 테스트의 단축 TTL은 운영 정책 변경이 아님 |
| 3 | 프로필·동의 | OperationalReadinessTest, ParticipationIntegrationTest. 미설정/누락/정책 버전 변경 차단, 필수 조건 완성 시 SHOW ME S 자동 지급; 사전 등록은 OPEN 후 재확인 |
| 4 | SHOW ME | ParticipationIntegrationTest. NFC 없이 소개·관심사·동의 조건, 사전 등록/동시 요청/중복 지급 방지 |
| 5 | SYNC ME | ResultsIntegrationTest. 승인된 유효 설문 필요, 최초 결과 한 번, 미응답/중복/미해금 거절 |
| 6 | MAKE A WISH | ContentIntegrationTest. 소원/설정, 제출→검수 중 미획득→승인 후 A, 거절/재접수/마감·경쟁 |
| 7 | FAST TAP | ResultsIntegrationTest. 연습은 공식 기록 없음, 공식 1회·준비 취소·중단·만료·검토 상태, 서버 검증 완료만 F |
| 8 | SAY 파이팅! | ContentIntegrationTest/ReactionIntegrationTest. 익명/공개·검수·수정본, 첫 승인 후 Y. 기존 하트 정책 유지 |
| 9 | 수집/동료 | ParticipationIntegrationTest/DiscoveryIntegrationTest. 서버 완료 기록·공개 동의·관심사/동일유형/방문 반영, 비공개 정보 차단 |
| 10 | 운영자 검수 | OperationsIntegrationTest. 일반 계정 403, 원문/현재 승인본 비교, 승인·거절·재처리·공개 관리의 버전 경쟁 및 사유/감사 기록 |
| 11 | 실제 UI | 기존 5차 frontend lint/build/test:unit, test:auth-nfc-ui/test:participation-ui/test:results-ui/test:content-ui/test:discovery-ui/test:operations-ui. 브라우저 fixture는 서버 통합 테스트와 별개 |

content_settings나 SYNC 문항을 이번에 임의 작성하지 않음. 검수 provider NONE이면 운영자 확인 흐름으로 갈 수 있으므로 자동 승인을 기대하면 안 됨. 임시 컨테이너 테스트의 mock 승인과 실제 운영 제공자 승인도 구별한다.

## 7. 이번 실행 결과

- PASS: 오프라인 도구 테스트 7개. 설정 누락/빈 정책/UTC·마감/loopback 외부 차단/GET만 허용/개발 SQL guard·ROLLBACK/읽기 전용 SQL 금지문 검사.
- PASS: 기존 5차 UI와 합친 별도 작업 공간에서 frontend lint/build 및 전체 Node 단위 26개(기존 19 + 새 도구 7). UI 파일 바이트 보존 확인.
- PASS: 적용·역적용 및 기본 86ad1dd/사용자 5차 패치 위 적용 검사 (ZIP 패키징 단계).
- 실행 차단: Java 테스트 시작 시 Gradle 배포 파일 다운로드 Network is unreachable. 설치 JDK 17뿐이며 필요 JDK 21도 없음. 새 JUnit 및 기존 MM/통합 테스트는 컴파일/실행 검증되지 않음.
- 미실행: 실제 MySQL/Redis 통합 (Work Docker 없음), SQL을 DB에서 실행, PowerShell 실행 (Work pwsh 없음), 브라우저 계약/모바일/NFC 실물, 실제 MM 재검증. 다른 DB로 대체하지 않음.
- 실제 DB 쓰기/실제 DM/실제 계정 생성/초기화, commit/push/PR/배포 없음.
