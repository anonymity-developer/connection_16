# 검증 결과 — 2026-11-08 KST

| 항목 | 결과 / 근거 |
| --- | --- |
| 프론트 ESLint | PASS — npm --prefix frontend run lint |
| TypeScript/Vite | PASS — npm --prefix frontend run build |
| 프론트·SSR·도구 | PASS 54/54 — npm --prefix frontend run test:unit. 기존 50개 재사용 + 시상 SSR 4개 |
| 실제 Java 순수 계산기 | PASS — Java17 compiler module로 production AwardModels/AwardCalculator와 독립 검증 harness를 컴파일·실행. 접수 순위/프로필 동점/공동 순위/중복 후보/불완전 근거/40개 동시 반복 계산. Spring/DB는 포함하지 않음 |
| UI 소스 보조 검사 | PASS — node scripts/taste.mjs lint frontend/src/features/operations/AwardsPanel.tsx --app, 0 FAIL / 0 WARN. 실렌더 검사 아님 |
| Spring/JDK21 test·bootJar | 미검증 — JDK21 없음. javac launcher도 없으나 Java17의 jdk.compiler module은 있어 위 순수 계산만 실행 가능했음 |
| MySQL/Redis 통합·SQL 실행 | 미검증 — Docker 없음. 다른 DB로 대체하지 않음 |
| 운영 API 권한·동시 DB 스냅샷 | 미검증 — 기존 OperationsIntegrationTest에 격리 MySQL/Redis 테스트 추가. Work HTTP/DB PASS 주장 없음 |
| 실제 모바일/데스크톱 브라우저 | 미검증 — 360/375/390/412/430/1440px fixture 계약 스크립트 작성, 실제 실행하지 않음 |
| PowerShell/실기기 | 미검증 — Windows 및 실제 기기에서 실행 필요 |
| 실제 운영 DB·MM·시상 | 미실행 — 조회/쓰기/DM/추첨/확정/지급 없음 |

Java 실제 실행 명령:

```bash
java --module jdk.compiler/com.sun.tools.javac.Main --release 17 -d /tmp/tagme-awards-check \
  backend/src/main/java/com/tagme/operations/AwardModels.java \
  backend/src/main/java/com/tagme/operations/AwardCalculator.java \
  backend/src/test/java/com/tagme/operations/AwardsCalculatorChecks.java
java -cp /tmp/tagme-awards-check com.tagme.operations.AwardsCalculatorChecks
```

실제 Work 실행은 격리 tmp/award-calculator 출력 폴더를 사용했다. 코드/클래스 로그만 남기고 실제 사용자 데이터는 사용하지 않았다. 전체 Spring 애플리케이션을 Java17로 실행하거나 지원 버전을 낮추지 않았다. Run-CalculatorCheck.ps1은 같은 순수 검증을 JDK의 javac로 실행하는 Windows 보조 도구이며 JDK21/DB 통합을 대체하지 않는다.

## 작성한 Java/DB 회귀

- AwardCalculatorTest/AwardsCalculatorChecks: 위 순수 로직을 JUnit에서도 재사용.
- OperationsIntegrationTest: 실제 현재 DB ADMIN/세션 권한, 401/403/no-store, 접수 시각 대 승인 지연/완료 순번, 최초 프로필/완전 동점, 수정 대기·새 승인본과 최초 evidence 보존, 댓글 중복/자기/숨김, 하트 중복 PK/취소/제공자 공개, VALID/REVIEW_REQUIRED/INTERRUPTED/비정상 점수, 누락·역행 근거, 반복 snapshot과 동시 숨김 커밋의 격리.
- 기존 ParticipationIntegrationTest의 SHOW ME 자동 지급/사전 등록/동시 완료 및 완료 순번 테스트는 변경 없이 유지. 시상 알고리즘에서 그 순번을 순위로 사용하지 않는다.

시상 API·SQL·Spring 코드는 JDK21+MySQL에서 컴파일/실행 검증이 남아 있다. 초기 구현이므로 실제 행사 시상에 사용하기 전에 반드시 위 회귀를 통과시켜야 한다. 조회 timestamp는 매번 바뀌어도 같은 원본 상태에서 후보 점수·순서는 동일해야 한다.

패치 적용·역적용의 실제 결과는 ZIP 루트 PATCH_VERIFICATION.md를 참조한다.
