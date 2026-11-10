# 部署（阿里云 ECS · Docker Compose）

> 本文只写实测过的内容。标注「待办」的部分需要控制台权限，我无法代做。

## 目标环境（实测）

| 项 | 值 |
|---|---|
| 公网 IP | `8.134.215.195` |
| 系统 | Alibaba Cloud Linux 3（RHEL 8 系，`dnf`） |
| 配置 | 2 vCPU / 1.8 GiB 内存 / 40 GiB 盘 / 4 GiB swap |
| Docker | 26.1.3，Compose v2.27.0，服务已启用 |
| 登录 | `ssh ecs`（g1027，密钥）；`ssh ecs-root`（救援） |
| 部署目录 | `/home/g1027/kdshop` |

## 拓扑

```
公网 :80 → nginx（静态 + /api 反代 + /media）
             ├── backend:8081   Spring Boot（JRE 21，-Xmx320m）
             ├── mysql:8.4      仅容器网络
             ├── redis:7        仅容器网络（会话）
             ├── minio          仅容器网络（上传图）
             └── mailpit        仅容器网络（邮件捕获）
```

只有 nginx 映射到宿主机 80；其余端口不对外。

## 内存分配（compose 里写死）

| 服务 | 上限 | 实测 |
|---|---|---|
| backend | 640m | ~315 MiB |
| mysql | 512m | ~242 MiB |
| minio | 224m | ~89 MiB |
| redis | 96m | ~17 MiB |
| mailpit | 64m | ~31 MiB |
| nginx | 32m | ~5 MiB |

整机 used ~1.05 GiB / 1.87 GiB，余量约 800 MiB。

## 部署步骤（在本地 WSL 执行）

```bash
cd /mnt/d/web-ai-code/KDShop

# 1. 构建前端（图片走同域 /media）与后端 jar
cd frontend && VITE_MEDIA_BASE=/media node node_modules/vite/bin/vite.js build && cd ..
cd backend && mvn -B -q -DskipTests package && cd ..

# 2. 发布到 ECS（tar 流传输，不依赖远端 rsync）
bash scripts/deploy-ecs.sh
```

脚本会：建目录 → 传前端/jar/配置 → 首次生成随机口令的 `.env` → `docker compose up -d` → 等待后端健康。

## 首次部署要点（已踩过）

1. **镜像拉取**：ECS 直连 Docker Hub 超时，`/etc/docker/daemon.json` 已配 `docker.m.daocloud.io` 镜像；`minio/minio` 与 `minio/mc` 镜像在该镜像站取不到，`minio/minio` 由本地 `docker save | ssh | docker load` 传入。
2. **mysql 镜像**：`mysql:8.4` 需要单独 `docker pull` 并重试，首次 compose up 会卡在大层下载。
3. **媒体图片**：演示图放在 `~/kdshop/media`（120 个文件，5.3 MB），nginx 先查静态文件，缺失时回落到 MinIO。商家新上传的图只进 MinIO，通过同一 `/media/` 回落可见。
4. **构建必须在本地**：2C1.8G 跑 `mvn package` 会非常慢甚至 OOM。

## 必须由你完成的两件事

1. **阿里云控制台 → 安全组 → 入方向放行 TCP 80**（来源建议先填你的出口 IP）。当前 80 从公网不可达，已实测确认。
2. **创建系统盘快照**：这台机器目前没有任何快照，误删或被黑无法恢复。

放行后访问：`http://8.134.215.195/`。

## 日常运维

```bash
ssh ecs
cd ~/kdshop

docker compose ps                  # 状态
docker compose logs -f backend     # 后端日志
docker compose restart backend     # 重启后端
docker compose down                # 停止（保留数据卷）
docker compose up -d               # 启动
docker stats --no-stream           # 内存占用
df -h /                            # 磁盘
```

## 备份

```bash
# 数据库
ssh ecs 'cd ~/kdshop && docker compose exec -T mysql mysqldump -uroot -p"$(grep MYSQL_ROOT_PASSWORD .env | cut -d= -f2)" kdshop' > kdshop-$(date +%F).sql

# MinIO 数据卷
ssh ecs 'docker run --rm -v kdshop_minio-data:/data -v ~/backup:/backup alpine tar -C /data -czf /backup/minio-$(date +%F).tgz .'
```

最稳的仍是控制台快照。

## 回滚

1. 代码：本地 `git revert` 后重新执行 `scripts/deploy-ecs.sh`
2. 容器：`docker compose down && docker compose up -d`（数据卷不变）
3. 数据：从快照或上面的备份恢复

## 已知限制

- **无 HTTPS**：没有域名与备案，密码在公网明文传输，只适合演示
- Mailpit 不对外，看邮件需要 SSH 隧道：`ssh -fN -L 8025:127.0.0.1:8025 ecs` 后开 `http://127.0.0.1:8025`
- 2C1.8G 跑 6 个容器属于紧平衡，日志增长与镜像堆积需要定期清理：`docker system prune -af`
