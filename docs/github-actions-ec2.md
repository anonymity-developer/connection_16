# GitHub Actions → Sydney EC2 배포

## 현재 상태 — 2026-11-10

운영 주소는 사용자가 가비아에서 구입한 `anonymitydeveloper.site`다. 시드니 `ap-southeast-2`, 기존 단일 VM/Compose, GitHub Actions 이미지 빌드 방식을 유지한다. 이 문서는 배포 구현과 실제 연결 상태를 구분한다.

- 구현: 검증된 main 커밋 → private ECR 이미지 두 개 → SSM Run Command → EC2 readiness/HTTPS smoke. SSH 키·장기 AWS 키와 실제 운영 `.env`는 GitHub에 넣지 않는다.
- AWS 읽기 확인: `tagme` 로그인 정상, Free 플랜 ACTIVE, 잔액 $100. EC2/ECR는 미생성이다. `tagme-ec2`라는 **빈 역할만 생성**됐고 정책/프로필은 아직 연결되지 않았다.
- 차단: `iam:ListOpenIDConnectProviders`는 실제 SCP explicit deny다. 제공자 생성은 자동 승인 검토가 명시적 승인 부족으로 거부해 **호출되지 않았다**. EC2 역할 정책·프로필/ECR/보안 그룹/SSM 설정 묶음도 자동 승인 검토가 거부해 실행되지 않았다. 이것을 AWS의 해당 API 거부로 오인하지 않는다.
- 로컬 `.env.production`은 새 DB/관리자/HMAC 비밀값과 실제 도메인으로 만들었다. Git 제외 상태이며 값은 출력하지 않았다. SSM 업로드는 아직 없다. 기존 `.env`/`.env.pr-review`를 변경하지 않았다.
- 도메인 공용 DNS 조회는 A/NS 모두 NXDOMAIN이었다. 등록/위임 전파와 EC2 IP 연결 후 다시 확인한다. 실제 EC2 기동·HTTPS·GitHub OIDC·원격 배포 성공은 아직 검증하지 않았다.
- 작업 브랜치 `infra/ec2-deploy`는 원격에 push했다. draft PR 생성도 자동 승인 검토가 별도 승인 없는 외부 GitHub 작업으로 거부했으므로 PR은 생성되지 않았다. GitHub CI/배포 실행 성공으로 기록하지 않는다.

## 실행 전 승인할 EC2 설정

| 항목 | 설정 |
| --- | --- |
| 이름/리전 | `tagme-event` / `ap-southeast-2` |
| 인스턴스 | `t3.small`, 2 vCPU / 2GiB, x86_64 |
| OS | Canonical Ubuntu 24.04 LTS, 조회한 AMI `ami-081307e05c61a3854` (`20261108`) |
| 네트워크 | 기존 default VPC `vpc-0bed5c060e997e925`, public subnet `subnet-014de1ee46d43bd64` |
| 디스크 | 암호화 gp3 30GiB, 종료해도 보존, 종료 보호 활성화 |
| 접속 | SSM, SSH 키/22 인바운드 없음 |
| 메타데이터 | IMDSv2 필수, hop limit 1: 호스트만 역할을 사용하고 앱 컨테이너는 접근하지 않음 |
| CPU/관측 | standard CPU credits, 기본 5분 지표; 추가 CPU 크레딧 요금 방지, 크레딧 소진 시 성능 제한 가능 |
| 공개 범위 | 처음에는 인바운드 없음. DNS 확인 후 Caddy TCP 80/443만 공개. API/DB/Redis 포트 공개 금지 |

2026-11-10 AWS Price List API 조회: Linux t3.small $0.0264/시간, gp3 $0.096/GiB·월, 사용 중 public IPv4 $0.005/시간. 730시간·디스크 30GiB 기준 **약 $25.80/월**, 전송/ECR/백업/세금 등은 별도다. 크레딧 차감 예상이며 무제한 무료가 아니다. 실제 플랜·잔액·만료와 사용액은 AWS Settings → Billing에서 관리한다.

현재 OIDC 제한이 고급 기능 활성화를 요구하는지 먼저 권한 상세에서 확인한다. 고급 기능 활성화는 유료 플랜 선행, 지출 한도 제거, 되돌릴 수 없는 조직 설정 변경이므로 사용자 선택 없이 수행하지 않는다. [AWS 공식 안내](https://docs.aws.amazon.com/accounts/latest/reference/activate-advanced-features.html).

## IAM과 AWS 리소스

템플릿은 [infra/ec2/iam](../infra/ec2/iam/)에 있다. `ACCOUNT_ID`와 `INSTANCE_ID`를 실제 값으로 치환한 파일은 Git 제외 디렉터리에 만든다. 템플릿 placeholder를 실제 비밀값으로 바꿔 커밋하지 않는다.

1. `tagme-ec2`에 [EC2 trust](../infra/ec2/iam/ec2-trust.json), `AmazonSSMManagedInstanceCore`, [runtime policy](../infra/ec2/iam/ec2-runtime.json)를 연결하고 같은 이름의 instance profile을 만든다. 앱은 AWS API를 직접 쓰지 않는다.
2. Sydney에 `tagme/api`, `tagme/web` ECR 저장소를 private / IMMUTABLE / AES256 / basic scan-on-push로 만든다. EC2는 두 저장소 pull만 허용한다. 배포한 태그와 이전 정상 태그는 정리 대상에서 제외한다.
3. GitHub OIDC 제공자는 `https://token.actions.githubusercontent.com`, audience는 `sts.amazonaws.com`이다. [GitHub trust](../infra/ec2/iam/github-trust.json)는 이 저장소의 **main 브랜치만** 허용한다. [deployment policy](../infra/ec2/iam/github-deploy.json)는 두 ECR 저장소 push와 **정확한 EC2 한 대**의 Run Command만 허용한다.
4. [bootstrap.sh](../infra/ec2/bootstrap.sh)를 비밀값 없는 user-data로 전달한다. Ubuntu의 서명 검증된 apt 패키지로 Docker/Compose/AWS CLI를 설치한다. SSM Online과 Docker를 확인한 뒤 배포한다.

조회한 `AmazonSSMManagedInstanceCore` v2에는 모든 파라미터의 GetParameter/GetParameters 권한도 포함돼 있었다. runtime policy는 다른 파라미터 읽기를 명시적으로 Deny하여 운영 파일 한 개로 제한한다. 추후 Patch Manager 등 다른 파라미터가 필요한 기능을 추가할 때 이 제한도 함께 검토한다.

SSM Run Command는 대상 호스트에서 root로 실행된다. 따라서 GitHub main/workflow 수정 권한은 실질적으로 운영 서버 권한이다. main 보호/필수 CI/관리자 최소화를 함께 적용한다. Actions role에 `GetParameter`를 직접 허용하지 않더라도 root 명령 권한이 비밀값 접근을 완전히 막지는 않는다.

## 운영 환경 파일

[.env.production.example](../.env.production.example)을 기준으로 별도 `.env.production`을 준비한다. 현재 로컬 실파일은 초기 생성됐으며 일반 로컬 `.env`와 별개다. 파일을 열어 비밀번호를 확인하고 안전하게 보관한다. 채팅/PR/스크린샷에 붙이지 않는다.

- `APP_DOMAIN=anonymitydeveloper.site`.
- DB 두 비밀번호와 관리자 비밀번호는 서로 다른 새 난수, `AUTH_HMAC_KEY`는 최소 32바이트 난수 Base64.
- `ADMIN_BOOTSTRAP_ID=admin:tagme`. 현재 참가자 인증은 `AUTH_ENABLED=false`, 검수는 `NONE`.
- 참가자 인증 활성화 전 실제 승인된 Mattermost 발신 계정의 토큰 **또는** 로그인 ID/비밀번호, 채널 ID **또는** 팀/채널 이름을 지정하고 권한/DM을 검증한다. 기존 PR 검토 설정의 발신 계정을 자동 복사하지 않았다.
- `$`, 공백, `#` 등이 있는 외부 비밀값은 Compose dotenv의 **단일 인용부호**로 감싼다. `source .env.production`을 실행하지 않는다.

검증/업로드 예시(프로필/리전은 고정):

```powershell
python infra/ec2/validate-env.py .env.production
aws ssm put-parameter --profile tagme --region ap-southeast-2 --name /tagme/production/env --type SecureString --tier Standard --value file://.env.production
```

Standard 파라미터는 4KiB 이내다. 실제 비밀값이 CLI 인수나 Run Command 본문에 들어가지 않게 `file://`를 사용한다. 기존 파라미터는 자동으로 덮어쓰지 않는다. 초기화된 MySQL의 비밀번호는 `.env`나 파라미터만 바꿔도 변경되지 않으므로 별도 계정 회전 절차를 따른다. HMAC 변경도 기존 인증 상태에 영향을 주므로 임의로 재생성하지 않는다.

## 가비아 DNS와 HTTPS

EC2 public IP가 확정된 뒤 My가비아 → 서비스 관리 → DNS 관리툴 → 도메인 설정에서 다음 레코드를 추가한다. [가비아 공식 매뉴얼](https://customer.gabia.com/manual/domain/287/1201).

| 타입 | 호스트 | 값 |
| --- | --- | --- |
| A | `@` | 실제 EC2 public IPv4 |

현재 기본 주소는 apex 하나다. www는 별도 지원을 구현/설정하기 전 추가하지 않는다. 사용하지 않는 AAAA/기존 포워딩이 다른 서버를 가리키지 않는지 확인한다. 인스턴스 stop/start로 자동 public IP가 바뀌면 DNS도 바꿔야 한다. 고정 IP가 필요하면 기존 public IP 대신 Elastic IP 한 개를 명시적으로 선택하며 남는 주소를 방치하지 않는다.

DNS가 EC2를 가리킨 뒤 TCP 80/443을 공개한다. Caddy가 인증서를 발급하고 HTTP를 HTTPS로 전환한다. 로그인은 실제 HTTPS·Secure 쿠키·CSRF·세션으로 검증한다. 도메인을 비워 배포하면 common+small+images 구성으로만 실행하고 포트는 하나도 publish하지 않는다.

## GitHub 연결과 최초 배포

1. 배포 변경을 main에 반영하고 기존 CI의 Frontend/Backend/Docker가 성공해야 한다.
2. 저장소 Settings → Secrets and variables → Actions → **Variables**에 `AWS_ROLE_ARN=arn:aws:iam::실제프로젝트ID:role/tagme-github-deploy`, `EC2_INSTANCE_ID=실제인스턴스ID`를 추가한다. 두 값은 비밀키가 아니다. `AUTO_DEPLOY`는 최초 수동 배포가 성공하기 전 비워둔다.
3. `Deploy Sydney EC2`를 main에서 수동 실행한다. main의 성공한 push CI가 있는 정확한 커밋만 허용한다. 이미지 태그는 `커밋SHA-runID-runAttempt`로 불변이다. 외부 Actions도 확인한 커밋 SHA에 고정했다.
4. 성공 후 `AUTO_DEPLOY=true`를 지정하면 main push의 CI 성공 시 자동 배포한다. PR/포크 CI는 배포하지 않는다. 취소로 migration이 끊기지 않게 배포 동시 실행을 직렬화하고 서버에서도 lock을 잡는다.

현재 연결된 GitHub 앱은 저장소 파일/PR 조회·작성은 가능하지만 Actions 변수/실행 등록 도구가 없다. `gh`도 로그인되어 있지 않다. 변수 설정/수동 실행은 GitHub UI 또는 별도로 인증한 CLI가 필요하다. AWS 브라우저 로그인 캐시를 GitHub Secrets에 넣는 방식으로 대체하지 않는다.

[send-release.py](../scripts/deploy/send-release.py)는 허용한 텍스트 파일 9개만 압축하고 검증된 registry/tag만 SSM 명령에 넣는다. 운영 `.env`는 포함하지 않는다. EC2의 [deploy.sh](../infra/ec2/deploy.sh)는 이미지를 먼저 pull하고, 기존 DB가 있으면 dump 성공을 확인한 후 `--no-build --wait`로 실행한다. 모든 확인이 성공해야 `/opt/tagme/current`를 갱신한다. 실패 시 자동 DB 롤백은 없으며 실제 컨테이너 상태를 확인해야 한다.

## 복구와 개장 전 남은 확인

- `/opt/tagme/backups`의 pre-deploy dump는 서버 내부 백업이다. 서버 밖 암호화 보관·주기 실행·복원 검증은 아직 구현/실행하지 않았다. 공개 운영 전에 별도 저장/복원 절차를 완료한다. DB 볼륨 삭제나 `down -v`로 실패를 해결하지 않는다.
- 코드 롤백은 현재/이전 release 디렉터리·`images.env`의 불변 태그를 사용해 명시적으로 실행한다. DB migration 호환성을 먼저 검토하고 파괴적 DDL을 자동 복구했다고 가정하지 않는다.
- 실제 Mattermost, 정책/행사 설정, NFC/휴대전화, HTTPS 관리자 로그인, 100명 부하·지연·OOM/CPU 크레딧은 별도 미검증이다.
- 현재 로컬 기존 `tagme-web-1`의 8088 포트에는 LAN 주소 publish도 남아 있다. 이는 이번에 수정하지 않은 기존 검토 스택이다. 개발 HTTP에서는 운영 로그인을 하지 않고 필요 없을 때 노출을 닫는다.
- 행사 후 EC2/보존 EBS/IP/ECR/파라미터/백업의 보관·삭제를 함께 결정한다. 인스턴스를 종료해도 보존 EBS와 할당된 Elastic IP는 자동으로 정리되지 않는다.
