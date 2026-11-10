#!/usr/bin/env bash
# 把本地构建产物发布到 ECS（g1027@8.134.215.195，ssh 别名 ecs）
set -euo pipefail

HOST="${ECS_HOST:-ecs}"
APP_DIR="kdshop"

echo "==> 准备远端目录"
ssh "$HOST" "mkdir -p ~/$APP_DIR/web ~/$APP_DIR/app"

echo "==> 上传前端（tar 流，不依赖远端 rsync）"
tar -C frontend/dist -czf - . | ssh "$HOST" "tar -C ~/$APP_DIR/web -xzf -"

echo "==> 上传后端 jar"
scp -q backend/target/kdshop-backend-0.1.0-SNAPSHOT.jar "$HOST:~/$APP_DIR/app/app.jar"

echo "==> 上传编排与配置"
scp -q infra/docker-compose.prod.yml "$HOST:~/$APP_DIR/docker-compose.yml"
scp -q infra/nginx.conf "$HOST:~/$APP_DIR/nginx.conf"

echo "==> 生成 .env（仅首次）并启动"
ssh "$HOST" 'bash -s' <<'REMOTE'
set -euo pipefail
cd ~/kdshop
if [ ! -f .env ]; then
  cat > .env <<EOF
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 16)
MYSQL_DATABASE=kdshop
MYSQL_USER=kdshop
MYSQL_PASSWORD=$(openssl rand -hex 16)
REDIS_PASSWORD=$(openssl rand -hex 16)
MINIO_ROOT_USER=kdshop
MINIO_ROOT_PASSWORD=$(openssl rand -hex 16)
PAY_SECRET=$(openssl rand -hex 24)
WEB_BASE_URL=http://8.134.215.195
MAIL_MODE=SMTP
MAIL_FROM=no-reply@kdshop.local
EOF
  chmod 600 .env
  echo "已生成 .env"
fi
docker compose up -d --remove-orphans
docker compose ps
REMOTE

echo "==> 等待后端健康"
for i in $(seq 1 40); do
  code=$(ssh "$HOST" "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/api/v1/categories || true")
  if [ "$code" = "200" ]; then echo "后端就绪（第 ${i} 次探测）"; break; fi
  sleep 5
done

echo "==> 最终状态"
ssh "$HOST" "cd ~/kdshop && docker compose ps && curl -s -o /dev/null -w 'local80=%{http_code}\n' http://127.0.0.1/"
