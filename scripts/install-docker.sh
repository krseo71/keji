#!/usr/bin/env bash
set -euo pipefail

# Official Docker Engine installation for Ubuntu/Debian.
# Usage:
#   sudo bash scripts/install-docker.sh
# After install, re-login or run `newgrp docker` so the current user picks up the docker group.

if [[ $EUID -ne 0 ]]; then
  echo "must run as root: sudo bash $0"
  exit 1
fi

# Detect distro
if [[ -f /etc/os-release ]]; then
  . /etc/os-release
  DISTRO="${ID:-ubuntu}"
  CODENAME="${VERSION_CODENAME:-$(lsb_release -cs 2>/dev/null || echo stable)}"
else
  echo "cannot detect distro"
  exit 1
fi

case "$DISTRO" in
  ubuntu|debian) ;;
  *) echo "unsupported distro: $DISTRO"; exit 1 ;;
esac

echo "==> removing old docker packages (if any)"
apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

echo "==> installing prerequisites"
apt-get update
apt-get install -y ca-certificates curl gnupg

echo "==> adding Docker GPG key"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL "https://download.docker.com/linux/${DISTRO}/gpg" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

echo "==> adding Docker apt repository"
ARCH="$(dpkg --print-architecture)"
echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/${DISTRO} ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

echo "==> installing docker engine + compose plugin + buildx"
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "==> enabling docker service"
systemctl enable --now docker

TARGET_USER="${SUDO_USER:-$(logname 2>/dev/null || echo root)}"
if [[ "$TARGET_USER" != "root" ]]; then
  echo "==> adding user '$TARGET_USER' to docker group"
  usermod -aG docker "$TARGET_USER"
  echo "    (re-login or run 'newgrp docker' to apply)"
fi

echo ""
docker --version
docker compose version
echo "done."
