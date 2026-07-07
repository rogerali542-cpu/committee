# 业委会 demo —— Linux amd64 服务器部署包

三个镜像均为 **linux/amd64**。后端连**外部云 MySQL**（火山 RDS `committee` 库），不打包数据库。

## 包内容
| 文件 | 说明 |
|---|---|
| `images.tar` | 三镜像（ywh-frontend / ywh-backend / ywh-ocr-asr），~414MB |
| `docker-compose.yml` | 编排（web 发布 80 端口，backend/ocr 内部网络） |
| `.env` | 环境变量（云库地址 + 各密钥）⚠️ 含密钥，勿外传 |
| `ywh_db_full.sql` | 初始数据（导入云库 committee 用） |
| `deploy.sh` | 一键部署脚本 |

## 前置条件
- 一台 **Linux x86_64** 服务器，已装 **Docker**（20.10+）和 **Docker Compose 插件**（`docker compose version` 能用）。
- 服务器能访问云 MySQL `mysql-ff09b505ed0b-public.rds.volces.com:3306`。
  ⚠️ 火山 RDS 有 **IP 白名单**：把**本服务器的公网/内网出口 IP** 加进 RDS 白名单；若服务器与 RDS 在同一 VPC，用私网地址免白名单更稳。

## 部署步骤
```bash
# 0) 先把数据导进云库（只需一次；在任一能连云库的机器上执行）
mysql -h mysql-ff09b505ed0b-public.rds.volces.com -u thinklight -p committee < ywh_db_full.sql

# 1) 把整个 server-bundle 目录 scp 到服务器，进入目录
cd server-bundle

# 2) 一键部署
bash deploy.sh
#   等同于： docker load -i images.tar && docker compose --env-file .env up -d
```

浏览器打开 `http://<服务器IP>/` 即可。

## 常用命令
```bash
docker compose ps                 # 看状态
docker compose logs -f backend    # 后端日志（排查连库/AI）
docker compose restart backend    # 重启后端
docker compose down               # 停止并移除容器（数据在云库，不受影响）
```

## 排障
- **首页空白 / 接口 500**：多半是连不上云库 → 看 `docker compose logs backend`，确认 RDS 白名单已放行本服务器 IP、`.env` 里 `DB_*` 正确。
- **AI 功能（OCR/纪要/语音）不可用**：容器需能出公网连豆包 ark/openspeech；`.env` 里 `DOUBAO_*/ARK_*/VOLC_*` 密钥要有效。
- **端口冲突**：改 `docker-compose.yml` 里 web 的 `"80:80"` 为别的宿主机端口，如 `"8080:80"`。
