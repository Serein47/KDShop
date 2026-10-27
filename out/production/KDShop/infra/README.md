# 本地基础设施

Docker Compose 管理 MySQL、Redis、MinIO、Mailpit 四个服务。全部跑在 WSL 的 Docker 里，数据目录 `/home/g1027/docker-data`。

## 启动

```bash
cd infra
cp .env.example .env     # 首次
docker compose up -d
docker compose ps
```

## 端口

| 服务 | 容器端口 | 宿主端口 | 说明 |
|---|---|---|---|
| MySQL | 3306 | 3307 | 避开本机已装的 MySQL 80 |
| Redis | 6379 | 6380 | |
| MinIO API | 9000 | 9000 | S3 接口 |
| MinIO 控制台 | 9001 | 9001 | http://localhost:9001 |
| Mailpit SMTP | 1025 | 1025 | 后端发信指向它 |
| Mailpit UI | 8025 | 8025 | http://localhost:8025 |

## 凭证（本地默认值，生产必须换）

| 服务 | 用户 | 口令 |
|---|---|---|
| MySQL | `kdshop` / `root` | `kdshop123` / `root123` |
| Redis | — | `redis123` |
| MinIO | `kdshop` | `kdshop123` |

## 镜像来源（重要）

Docker Hub 在本网络不可直连，daemon 配了镜像加速 `https://docker.m.daocloud.io`。但部分仓库被加速器拒绝：

| 镜像 | 可用来源 |
|---|---|
| `mysql:8` `redis:7` | daocloud 加速直接可用 |
| `axllent/mailpit` | `docker.m.daocloud.io/axllent/mailpit`，pull 后 tag 成原名 |
| `minio/minio` | `docker.1panel.live/minio/minio`，pull 后 tag 成原名 |

拉取脚本见 `scripts/pull-images.sh`，会按顺序尝试并自动打 tag。

## 说明

- 容器数据在命名卷里，`docker compose down` 不删数据；`down -v` 才清空
- WSL 启动时通过 `/etc/wsl.conf` 的 `[boot] command` 拉起 dockerd；没有 systemd
- 从 Windows 访问一律用 `127.0.0.1:<宿主端口>`，WSL2 会自动转发
