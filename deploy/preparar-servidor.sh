#!/usr/bin/env bash
# Prepara una instancia EC2 nueva (Ubuntu 24.04, x86_64) para AutoReel. Se pega como "User data" al lanzar la
# instancia, o se ejecuta una vez con: sudo bash preparar-servidor.sh
#
# Instala Docker y el plugin de compose de Ubuntu, deja al usuario ubuntu en el grupo docker, crea /opt/autoreel y
# agrega 2 GB de swap (la JVM y FFmpeg comparten los 4 GB de una t3.medium). Se puede repetir sin problema.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "Ejecuta este script como root (sudo bash preparar-servidor.sh)." >&2
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y docker.io docker-compose-v2 curl ca-certificates
systemctl enable --now docker

usermod -aG docker ubuntu

mkdir -p /opt/autoreel
chown ubuntu:ubuntu /opt/autoreel

if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
    if [ ! -f /swapfile ]; then
        fallocate -l 2G /swapfile
        chmod 600 /swapfile
        mkswap /swapfile
    fi
    swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab

echo "Servidor listo: $(docker --version), $(docker compose version)."
