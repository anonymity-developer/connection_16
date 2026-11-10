#!/usr/bin/env bash
# Ubuntu 24.04 user-data. Contains no credentials; apt verifies signed packages.
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --yes docker.io docker-compose-v2 awscli curl python3
systemctl enable --now docker
install -d -m 0700 /opt/tagme /opt/tagme/releases /opt/tagme/backups
# Host tools use the instance role. Application containers do not need IMDS.
docker compose version
aws --version
