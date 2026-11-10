# EC2 초기 배포 — 한 달 이내 행사 운영

최대 동시 접속 100명 예상, 신규 AWS 계정, 단일 VM의 기존 Docker Compose를 사용한다. `compose.small.yml`은 2GiB 서버용 시작 설정이며 100명 처리 성능을 보증한 결과가 아니다. 실제 AWS 서버·DNS·Mattermost 연결·100명 부하는 서버 생성 후 확인한다. AI 검수 추가 연동과 구조 재작성은 이번 초기 배포 범위에서 제외한다.

## 1. 계정 가입

1. [AWS 공식 무료 계정](https://aws.amazon.com/free/)에서 가입한다. 화면에서 요구하는 본인 확인을 완료하고 Free plan을 유지한다. 지원 플랜 선택이 나오면 무료 Basic을 사용한다.
2. 콘솔의 현재 크레딧과 만료일을 확인한다. 2026-11-10 공식 안내는 신규 가입 $100와 활동에 따른 추가 최대 $100, Free plan은 최대 6개월 또는 크레딧 소진까지다. 지급 여부는 실제 계정 화면을 기준으로 한다. [현재 조건](https://aws.amazon.com/free/free-tier-faqs/).
3. 계정 보안 설정에서 MFA를 등록한다. 장기 액세스 키를 초기 배포의 필수 조건으로 만들지 않는다.

Free tier eligible 표시는 무제한 무료가 아니다. EC2 외 디스크·공인 IP·백업 등의 사용량도 합산해 잔액을 관리한다. Free plan은 종료 시 서비스가 중단될 수 있으므로 행사 기간까지의 잔액과 만료를 확인한다.

## 2. 첫 서버 생성

가입 후 EC2 콘솔의 인스턴스 시작에서 다음 값을 사용한다. 인스턴스 시작 전에 실제 표시 요금과 Free plan 대상 여부를 확인한다.

| 항목 | 초기 선택 |
| --- | --- |
| 리전 | 아시아 태평양(서울), `ap-northeast-2` |
| 이름 | `tagme-event` |
| OS | Canonical Ubuntu Server 24.04 LTS, x86_64, 일반 AMI |
| 인스턴스 | `t3.small`, vCPU 2 / 메모리 2GiB |
| 디스크 | gp3 30GiB, 암호화 활성화 |
| 키 페어 | 새 키 페어 생성 후 로컬에 보관 |
| 퍼블릭 IP | 웹 접속용 IPv4 활성화 |
| 보안 그룹 | TCP 22는 내 IP, TCP 80/443은 웹 사용자 접속 허용 |

MySQL 3306, Redis 6379, API 8080을 외부에 열지 않는다. 계정 제한으로 위 사양을 선택할 수 없으면 콘솔의 구체적인 제한을 먼저 확인한다. 임의로 유료 플랜 전환이나 더 큰 서버를 생성하지 않는다. 2GiB가 부족하면 부하 측정과 예상 크레딧 소진액을 비교해 사양을 변경한다.

[신규 프리티어 대상](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html). 가입·키 보관은 사용자 기기에서 수행하며 PEM·비밀번호·토큰은 채팅/저장소에 넣지 않는다.

## 3. 메모리가 사용되는 이유와 예산

기존 로컬 측정에서 API 약 560MiB, MySQL 약 476MiB, Redis 약 7MiB, Caddy 약 15MiB였다. 이 값은 컨테이너 사용량이며 Java 힙만을 의미하지 않는다. 클래스·네이티브 버퍼·스레드, DB 캐시·진단 기능도 포함한다. 데이터나 참가자 수에 단순 비례하지 않는다.

기존 API는 컨테이너 상한 없이 `MaxRAMPercentage=70`을 사용해 호스트 메모리를 기준으로 확장할 수 있었다. MySQL은 연결 상한 151, Performance Schema 활성화, 내부 임시 테이블의 RAM 상한 1GiB였다. 상한은 예약 사용량이 아니지만 작은 서버에 맞춘 제한이 필요하다.

| 서비스 | RAM 상한 | 조정 |
| --- | --- | --- |
| API | 768MiB | 힙 128~384MiB, DB 풀 최소 2 / 최대 10 |
| MySQL | 640MiB | 버퍼 풀 128MiB, 로그 버퍼 16MiB, 연결 상한 60, 임시 테이블 RAM 64MiB |
| Redis | 192MiB | 데이터 상한 64MiB, AOF와 noeviction 유지 |
| Caddy | 96MiB | 정적 React 파일과 API 프록시 |

합계는 1696MiB로 2GiB 중 나머지를 OS/Docker에 남긴다. 각 제한이 곧 실제 사용량은 아니다. MySQL Performance Schema를 끄므로 해당 상세 진단은 사용할 수 없다. 이 설정에서도 OOM/지연은 부하 시험으로 확인한다. [Docker 자원 제한](https://docs.docker.com/engine/containers/resource_constraints/), [MySQL 메모리](https://dev.mysql.com/doc/refman/8.4/en/memory-use.html).

## 4. 서버 생성 후 진행 순서

1. SSH 연결 및 Docker Engine/Compose 설치.
2. 개발 PC/CI에서 검증된 이미지를 만들고 서버로 전달. 작은 운영 서버에서 Gradle/npm 빌드를 함께 실행하지 않는다. 이미지 전달 방식과 실제 태그는 배포 시 확정한다.
3. 운영 전용 환경 파일 준비. 개발 DB 비밀번호나 로컬 Compose 프로젝트 설정을 그대로 복사하지 않는다. 관리자 계정과 HMAC, 실제 Mattermost 설정을 서버에서 지정한다.
4. 사용할 도메인/무료 서브도메인을 서버 IP에 연결하고 `APP_DOMAIN`을 지정한다. Caddy가 HTTPS를 제공한다.
5. 아래 구성으로 실행하되 실제 배포 명령에는 전달한 이미지 설정과 `--no-build`를 사용한다.

```bash
docker compose -f compose.yml -f compose.prod.yml -f compose.small.yml --profile app config --quiet
```

운영에는 `compose.dev.yml`을 섞지 않는다. 현재 Compose의 build 항목만으로 사전 빌드 이미지 전달이 완성되는 것은 아니며, 이미지 태그·전달·복구를 준비한 뒤 실제 up을 수행한다.

6. HTTPS 로그인·관리자 조회·Mattermost·행사 설정·정책·네 NFC 태그와 문항을 확인한다. DB를 새로 만들면 로컬에서 쓰던 행사 설정은 자동으로 따라가지 않는다.
7. DB의 주기적 백업과 서버 밖 보관/복원을 확인한다. 기존 [백업 스크립트](../infra/backup/backup.sh)는 서버 내부 dump 생성까지만 제공한다.
8. 100명 동시 로그인/프로필 조회/게임 제출 시나리오로 CPU·RAM·지연·오류·Redis·DB를 측정한다. 행사 후 보관 정책에 따른 자료 정리와 클라우드 자원 종료를 진행한다.

## 5. 로컬 메모리 설정 검증

기존 `.env`가 가리키는 개발 스택에만 적용한다. 기존 DB 볼륨은 유지한다.

```bash
docker compose -f compose.yml -f compose.dev.yml -f compose.small.yml --profile app config --quiet
docker compose -f compose.yml -f compose.dev.yml -f compose.small.yml --profile app up -d --build --wait
docker stats --no-stream
```

기본 자원 설정으로 돌아갈 때는 원래 개발 Compose 두 파일로 다시 실행한다. 데이터 볼륨을 삭제할 필요는 없다. 작은 설정 변경은 DB/API 재시작을 수반하므로 서비스 이용 중에는 적용 시점을 정한다.

2026-11-10 로컬 검증: 네 컨테이너 healthy, 실제 관리자 로그인·다섯 미리보기·로그아웃·공식 테이블 행 수 무변경과 웹/API 메타 smoke를 통과했다. 저부하 스냅샷 합계 약 1057MiB → 623MiB, OOM 종료 없음·재시작 0회였다. 실제 EC2 2GiB 환경과 100명 부하는 아직 미검증이다.
