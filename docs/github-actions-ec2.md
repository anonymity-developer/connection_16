# GitHub Actions → EC2 운영 배포

현재 배포 경로는 **성공한 main CI → 이미지 빌드와 공개 배포 릴리스 → EC2의 검증·적용**이다. GitHub에 AWS/SSH 키나 운영 환경 파일을 넣지 않는다. 이 문서는 구현된 배포 절차이며 최신 릴리스가 실제 서버에 적용됐음을 보증하는 상태판은 아니다.

## 구성

- 운영 주소: `https://anonymity-developer.site`
- 리전: Sydney `ap-southeast-2`, 단일 Ubuntu EC2 `t3.small`(2 vCPU, 2GiB)
- Caddy·API·MySQL·Redis를 각각 컨테이너로 실행한다. 앱 이미지는 CI에서 빌드하고 DB/Redis는 호스트의 named volume을 사용한다.
- 운영 설정은 SSM SecureString `/tagme/production/env`에서 root 전용 파일로 주입한다. Git·릴리스·로그에 비밀번호나 환경 파일을 포함하지 않는다.
- 공개 포트는 HTTP/HTTPS이며 운영 접속은 SSM을 사용한다. DB/Redis 포트는 외부 공개하지 않는다.
- AWS OIDC/ECR 경로는 현재 사용하지 않는다. 무료 플랜의 제약 때문에 공개 릴리스 폴링 방식을 사용한다. 인스턴스 중지/시작 시 주소 변경과 DNS 영향을 별도로 확인한다.

## 릴리스와 적용

1. [CI](../.github/workflows/ci.yml)가 프론트·백엔드·통합·이미지 경계 검사를 수행한다.
2. [배포 워크플로](../.github/workflows/deploy.yml)는 현재 main의 성공한 push CI를 확인한다. 저장소 Actions variable `AUTO_DEPLOY=true`이면 자동 발행하며 수동 실행도 같은 검증을 거친다.
3. [패키저](../scripts/deploy/package-release.py)가 허용한 runtime 파일·두 이미지·체크섬·커밋/실행 정보를 공개 릴리스에 넣는다. 운영 데이터와 fixture 소스는 포함하지 않는다.
4. [EC2 controller](../infra/ec2/poll-release.py)를 [systemd timer](../infra/ec2/tagme-deploy.timer)가 약 5분마다 실행한다. 현재 main SHA, 성공한 정확한 CI/배포 실행·시도, 게시자/저장소, 크기·체크섬·허용 파일·이미지를 확인한다.
5. [deploy.sh](../infra/ec2/deploy.sh)가 DB dump를 성공한 뒤 이미지를 load하고 `--no-build --wait`로 적용한다. 호스트 잠금으로 동시 배포를 막으며 모두 성공해야 `/opt/tagme/current`를 변경한다.

Compose는 기본·prod·small 설정과 릴리스가 만든 이미지 override를 결합한다. GitHub API 제한·릴리스 지연·검증 실패 시 마지막 정상 서비스를 유지한다. 이 구조는 GitHub 저장소/워크플로 권한을 신뢰하며 별도 콘텐츠 서명 시스템은 아니다.

## 확인과 복구

승인된 운영 세션에서 확인한다. 명령 출력에 환경 파일·세션·본문을 추가하지 않는다.

```bash
sudo systemctl status tagme-deploy.timer
sudo journalctl -u tagme-deploy.service -n 50 --no-pager
# 새 적용을 명시적으로 요청할 때
sudo systemctl start tagme-deploy.service
```

- PR CI 성공과 main 병합, 릴리스 발행, EC2 적용은 각각 별개다. 서비스 health와 HTTPS smoke, 실제 실행 이미지의 SHA를 함께 확인한다.
- 이전 정상 이미지/runtime을 보존하고 Flyway 호환성을 검토한 뒤 명시적으로 복구한다. 실패한 migration의 자동 DB 롤백은 제공하지 않는다.
- MySQL/Redis 볼륨을 삭제하는 명령을 운영 종료 절차로 쓰지 않는다. Redis 장애로 세션이 사라져도 MySQL의 계정·게임 기록은 별도 보존된다.
- `/opt/tagme/backups`는 서버 내부 dump이며 서버/디스크 장애와 분리된 백업이 아니다. 서버 밖 암호화 보관·주기 실행·복원 연습은 별도 운영 확인이 필요하다.
- 오래된 이미지/릴리스/백업의 용량을 점검하되 현재·이전 정상 릴리스를 남긴다. 실제 계정·태그·참여 기록을 검증용으로 덮어쓰지 않는다.

## 설정 변경

[환경 예시](../.env.production.example)와 [validator](../infra/ec2/validate-env.py)를 기준으로 한다. 운영 파일은 Git 밖에서 다루고 기존 DB 비밀번호·HMAC을 파일만 바꿔 임의 회전하지 않는다. SSM 변경은 운영 승인을 받은 작업에서만 수행하며 이 문서 검토는 서버 설정을 변경하지 않는다.

실물 NFC·Mattermost DM·기기/네트워크·행사 설정·부하·백업 복원은 [행사 운영](operations.md)의 별도 점검 대상이다. 현재 AWS 잔액·과금·서버 상태를 과거 확인 값으로 단정하지 않는다.
