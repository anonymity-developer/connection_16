# GitHub Actions → Sydney EC2 배포

## 현재 배포 방식

사용자는 2026-11-10 EC2·배포 권한 구성, PR/CI 통과 후 main 병합·배포, 기존 승인된 Mattermost 발신 계정의 운영 사용, 로컬 스택 종료를 승인했다. AWS의 OIDC 생성은 실제 SCP explicit deny였고 사용자는 **Free 플랜 유지**를 선택했다. 고급 기능/유료 전환이나 SCP 변경은 하지 않는다.

현재 구현은 **main의 성공한 CI → GitHub Actions 이미지 빌드 → 공개 배포 릴리스 → EC2가 검증 후 실행**이다. GitHub에 AWS/SSH 키나 운영 환경 파일을 넣지 않는다. 공개 저장소의 코드/이미지만 공개하며 DB·참가자 자료·실제 환경변수는 릴리스에 포함하지 않는다. 초기 ECR/OIDC 경로는 현재 배포에 사용하지 않는다.

## 리소스와 확인된 상태

| 항목 | 값 |
| --- | --- |
| 운영 도메인 | 가비아 `anonmitydeveloper.site` |
| EC2 | `i-0faa77905f491ab31`, `tagme-event` |
| 리전 / IP | Sydney `ap-southeast-2` / `13.236.85.206` |
| OS / 인스턴스 | Ubuntu 24.04 / `t3.small`, 2 vCPU, 2GiB |
| 디스크 | 암호화 gp3 30GiB, 종료 후 보존, 종료 보호 활성화 |
| 접속 / SG | SSM Online, `sg-0ad6f90dcad703475`, TCP 80/443만 공개 |
| 메타데이터 | IMDSv2 필수, hop limit 1; 앱은 AWS API를 직접 사용하지 않음 |
| CPU / 관측 | standard credits / 기본 5분 지표 |
| 운영 비밀값 | SSM SecureString `/tagme/production/env`, EC2 root 전용 파일 |

AWS CLI는 Ubuntu 저장소에 설치 후보가 없어 공식 2.39.7 ZIP과 PGP 서명을 검증해 설치했다. [bootstrap.sh](../infra/ec2/bootstrap.sh)는 공식 키 지문 `FB5DB77FD5C118B80511ADA8A6310ACC4672475C`를 확인한다. 실제 EC2에서 Docker/Compose/AWS CLI 설치 성공을 확인했다. 최초 cloud-init 실패 기록은 이력이며 같은 서버에서 SSM으로 수정 설치했다.

PR [#15](https://github.com/anonymity-developer/connection_16/pull/15)의 최종 Frontend/Backend/Docker와 병합 후 main CI가 모두 성공했다. 첫 [배포 실행](https://github.com/anonymity-developer/connection_16/actions/runs/42788902331)의 main `d0dcbb37542ead25252a43941774b7af1ab2ebfd` 릴리스를 EC2에 적용해 네 컨테이너 healthy, HTTPS 웹/API smoke를 확인했다. 실제 HTTPS에서 CSRF 없는 로그인 403, 관리자 로그인, `TAGME_SESSION` Secure/HttpOnly, 관리자 조회, 로그아웃 후 401을 통과했다. `AUTO_DEPLOY=true`를 설정했고 이후 main push CI 성공 시 자동 릴리스 발행/EC2 반영이 활성화돼 있다.

실제 등록 주소에는 `anonymity`의 `y`가 없는 `anonmitydeveloper.site`를 사용한다. 초기에 전달된 다른 철자는 공용 등록/DNS에 없었으며, 사용자 등록 정보 확인 후 운영 파라미터를 수정했다. 정식 주소의 A 레코드가 서버 IP를 가리키고 HTTPS가 유효한 것을 확인했다.

2026-11-10 Price List 조회 기준 서버+30GiB+IPv4는 730시간당 약 **$25.80**, 전송/백업 등은 별도다. 당시 Free ACTIVE, 잔액 $100을 확인했다. CPU 크레딧 소진 시 성능이 제한될 수 있다. IP는 stop/start 시 바뀔 수 있으므로 재시작 전 DNS 영향을 확인한다. 종료해도 보존 EBS는 자동 삭제되지 않는다.

초기 준비에서 만든 private ECR `tagme/api`, `tagme/web`는 비어 있고 현재 배포에는 사용하지 않는다. `tagme-ec2` 역할은 SSM core와 운영 파라미터 읽기, 초기 두 ECR 저장소 읽기 권한을 가진다. SSM 기본 정책의 광범위한 파라미터 읽기는 [runtime policy](../infra/ec2/iam/ec2-runtime.json)의 명시적 Deny로 운영 파일 한 개에 제한한다. 미사용 ECR/권한 정리는 운영 변경과 구분해 추적한다.

## 배포 검증과 권한 경계

[배포 워크플로](../.github/workflows/deploy.yml)는 현재 main 커밋의 성공한 push CI를 확인하고 이미지를 `SHA-runID-runAttempt`로 빌드한다. 앱 빌드는 EC2에서 하지 않는다. [packager](../scripts/deploy/package-release.py)는 허용한 runtime 파일 10개, 이미지 두 개, 체크섬/커밋/실행 정보를 릴리스에 넣는다.

[EC2 controller](../infra/ec2/poll-release.py)는 다음을 모두 확인한 뒤 이미지를 load하고 배포한다.

- 현재 main SHA와 일치하고 성공한 push CI가 있을 것.
- 정확한 배포 워크플로의 성공한 main 실행/시도 번호와 일치할 것.
- GitHub Actions bot이 게시한 일반 릴리스/에셋이고 정해진 저장소 URL일 것.
- 다운로드 크기·SHA-256이 맞고 runtime 압축에 허용된 일반 파일만 있을 것.
- 이미지 압축에는 해당 태그의 `tagme-api`, `tagme-web`만 있을 것.

이 검증은 GitHub 저장소/워크플로 권한을 신뢰한다. main/workflow를 바꾸거나 정상 릴리스 게시 권한을 탈취한 공격자는 서버 코드를 바꿀 수 있으므로 main 보호·필수 CI·계정 MFA·최소 collaborator 권한이 중요하다. GitHub 익명 API 제한/장애는 배포를 지연시키며, 마지막 정상 서비스는 유지한다. 별도 콘텐츠 서명 체계를 구현했다고 주장하지 않는다.

[systemd timer](../infra/ec2/tagme-deploy.timer)는 약 5분마다 실행하고 host lock으로 동시 배포를 막는다. [deploy.sh](../infra/ec2/deploy.sh)는 기존 DB dump를 성공한 뒤 `--no-build --wait`로 실행한다. 중지/고아 DB는 먼저 복구하게 하고 모든 확인이 성공해야 `/opt/tagme/current`를 바꾼다. migration 실패의 자동 DB 롤백은 없다.

```bash
sudo systemctl start tagme-deploy.service
sudo systemctl status tagme-deploy.timer
sudo journalctl -u tagme-deploy.service -n 50 --no-pager
```

원격 제어는 승인된 SSM을 사용한다. root/Docker/SSM 권한은 운영 비밀값 접근 권한이므로 명령 출력이나 로그를 공개 CI로 복사하지 않는다. 릴리스가 없거나 아직 게시 작업이 끝나지 않으면 controller는 기다린다.

## 운영 환경과 DNS

[.env.production.example](../.env.production.example)을 기준으로 Git 제외 `.env.production`을 만들었다. 서로 다른 새 DB/관리자 비밀번호·HMAC 키를 생성했고 기존 승인된 발신 계정/채널을 사용자 승인 후 반영했다. `AUTH_ENABLED=true`, 검수 `NONE`이다. 기존 `.env`/`.env.pr-review`는 보존하고 두 로컬 스택은 볼륨을 유지한 채 중지했다.

비밀값을 채팅/PR/명령 인수에 붙이지 않는다. dotenv는 실행하지 않으며 `$`/공백 등이 있는 값은 단일 인용부호로 감싼다. 기존 DB 비밀번호나 HMAC은 파일만 바꿔 임의 회전하지 않는다.

```powershell
python infra/ec2/validate-env.py .env.production
aws ssm put-parameter --profile tagme --region ap-southeast-2 --name /tagme/production/env --type SecureString --tier Standard --value file://.env.production --overwrite
```

기존 값 변경은 운영 영향과 실제 DB/외부 연동을 확인한 후 수행한다. Standard 파라미터는 4KiB 이내다. 서버는 배포할 때 읽으며 GitHub에서 읽지 않는다.

가비아 My가비아 → 서비스 관리 → DNS 관리툴에서 사용자가 아래 A 레코드 저장 완료를 알렸다. 공용 DNS와 인증서 발급은 별도로 확인한다. [가비아 안내](https://customer.gabia.com/manual/domain/287/1201).

| 타입 | 호스트 | 값 |
| --- | --- | --- |
| A | `@` | `13.236.85.206` |

기본 주소는 apex 하나다. 불필요한 AAAA/포워딩을 추가하지 않는다. Caddy가 인증서를 발급하고 HTTP를 HTTPS로 전환한다. 실제 HTTPS·Secure 쿠키·CSRF·관리자 로그인을 검증했다.

## GitHub 설정과 복구

첫 배포는 main CI 성공 뒤 `Deploy Sydney EC2`를 main에서 수동 실행해 검증했다. 저장소 Actions **Variable** `AUTO_DEPLOY=true`를 지정했으므로 이후 main push CI 성공 때 릴리스를 자동 발행한다. AWS_ROLE_ARN/EC2_INSTANCE_ID나 GitHub Secrets는 필요 없다. 기존 Git credential helper의 인증을 비밀값 출력 없이 CLI에서도 확인했다.

배포 실패 때 `/opt/tagme/current`와 실제 컨테이너 상태를 함께 확인한다. 이전 release의 `images.env` 태그/이미지를 보관하고 migration 호환성을 검토한 후 명시적으로 복구한다. 파괴적 migration을 이미지 롤백으로 복구했다고 가정하지 않는다. 릴리스 이미지/서버 다운로드/DB dump가 누적되므로 디스크 사용량을 확인하고 현재·이전 정상 배포를 보존하며 정리한다.

`/opt/tagme/backups`의 dump는 서버 내부 백업이다. 서버 밖 암호화 보관·주기 실행·복원 검증은 아직 완료하지 않았다. 공개 행사 전 완료해야 한다. 승인된 Mattermost 발신 계정의 로그인·채널 멤버십은 실제 EC2에서 확인하고 임시 세션을 종료했다. 실제 DM 발송·NFC/휴대전화·행사/정책 설정·100명 부하는 별도 미검증이다. 실제 최종 기동/CI/HTTPS 결과는 [진행 기록](progress.md)의 최신 항목을 따른다.
