#!/usr/bin/env bash
# 在 WSL(Ubuntu) 里安装 Docker Engine，数据目录放在 g1027 用户下。
# 使用：sudo bash scripts/wsl-bootstrap.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 sudo 运行：sudo bash scripts/wsl-bootstrap.sh" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
echo "==> 安装 docker.io 与 uidmap"
apt-get update
apt-get install -y docker.io uidmap curl ca-certificates

echo "==> 安装 docker compose v2"
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL -o /usr/local/lib/docker/cli-plugins/docker-compose \
  https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

echo "==> 配置镜像加速与数据目录"
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'JSON'
{
  "registry-mirrors": ["https://docker.m.daocloud.io"],
  "data-root": "/home/g1027/docker-data",
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
mkdir -p /home/g1027/docker-data
chown g1027:g1027 /home/g1027/docker-data

echo "==> 让 g1027 免 sudo 使用 docker"
usermod -aG docker g1027

echo "==> WSL 启动时自动拉起 docker"
if ! grep -q '\[boot\]' /etc/wsl.conf 2>/dev/null; then
  printf '[boot]\ncommand = service docker start\n' >> /etc/wsl.conf
fi

echo "==> 启动 docker"
service docker restart 2>/dev/null || service docker start
sleep 3

echo "==> 结果"
docker version --format 'docker {{.Server.Version}}'
docker info --format 'data-root={{.DockerRootDir}} mirror={{index .RegistryConfig.Mirrors 0}}'
docker compose version
echo "完成。新开一个 WSL 会话让 docker 组生效，或执行：newgrp docker"
