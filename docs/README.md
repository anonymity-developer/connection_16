# TAG ME 문서 안내

문서는 현재 `main`의 구현과 운영 경계를 설명합니다. 과거 PR별 인수인계와 일회성 검증 기록은 제거했으며, 실제 동작은 코드·Flyway migration·OpenAPI를 최종 기준으로 봅니다.

## 먼저 읽기

| 목적 | 문서 |
| --- | --- |
| 서비스와 미션 이해 | [제품 개요](overview.md), [유저 플로우](user-flows.md) |
| 로컬 실행과 테스트 | [개발 가이드](development.md), [Codex 셋업](codex-setup.md) |
| 화면 구성·반응형 기준 | [UI 기준](ui-ux.md) |
| 인프라 처음 이해하기 | [서버·DB·Redis 안내](infrastructure.md) |
| 시스템 구조 파악 | [아키텍처](architecture.md), [데이터 모델](data-model.md), [ERD](erd.md) |
| 행사·관리자 운영 | [행사 운영](operations.md), [관리자 콘솔](admin-console.md), [운영 배포](github-actions-ec2.md) |
| 현재 완료 범위 확인 | [구현 상태](progress.md) |

## 도메인 계약

| 영역 | 정책·동작 | API 계약 |
| --- | --- | --- |
| 인증 | [인증](auth.md), [Mattermost 프로필](mattermost-profile.md) | [auth.openapi.yaml](auth.openapi.yaml) |
| 프로필·참여·NFC | [참여](participation.md) | [participation.openapi.yaml](participation.openapi.yaml) |
| SYNC·FAST TAP | [결과 미션](results.md), [미션 규칙](mission-rules.md), [판정 복구·순위·재도전](fast-tap-support.md) | [results.openapi.yaml](results.openapi.yaml) |
| 소원·응원 | [콘텐츠](content.md), [직접 공개와 초기화](direct-publication-and-reset.md) | [content.openapi.yaml](content.openapi.yaml) |
| 댓글·하트 | [반응](reactions.md) | [reactions.openapi.yaml](reactions.openapi.yaml) |
| 동료 탐색 | [탐색](discovery.md), [추천 경계](recommendation-design.md) | [discovery.openapi.yaml](discovery.openapi.yaml) |
| 관리자 | [관리자 콘솔](admin-console.md), [운영 API](operations-admin.md), [지원 조치](support-and-member-controls.md) | [admin.openapi.yaml](admin.openapi.yaml), [operations.openapi.yaml](operations.openapi.yaml) |

주요 경로와 공통 오류 형식은 [API 개요](api.md)를 먼저 봅니다.

## 운영 보조 자료

- [OTP 안내 문구](auth/otp-message.md)
- [행사 종료 후 결과 API](post-event/API.md)
- [활동 데이터 정의](post-event/DATA_DICTIONARY.md)
- [행사 종료 체크리스트](post-event/OPERATIONS_CHECKLIST.md)
- [결과 보고서 템플릿](post-event/REPORT_TEMPLATE.md)

## 문서 관리 원칙

- 구현 완료와 계획을 섞지 않습니다. 계획은 이슈/PR에 두고, 병합 후 현재 문서만 갱신합니다.
- 비밀번호, 세션, 원본 NFC 토큰, 운영 `.env`, 실제 참가자 정보와 DB 추출물은 문서에 기록하지 않습니다.
- 정책 변경은 해당 도메인 문서와 OpenAPI, 데이터 변경은 migration과 데이터 문서를 함께 갱신합니다.
- 문서 링크와 예시는 PR 전에 검사하며, 오래된 실행 결과를 현재 상태처럼 보존하지 않습니다.
