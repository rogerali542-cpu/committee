#!/usr/bin/env bash
# 业委会 demo —— 一键部署（Linux amd64 服务器）
# 前置：已装 Docker + Docker Compose 插件；已把 ywh_db_full.sql 导入云库 committee。
set -e
cd "$(dirname "$0")"

echo "==> [1/3] 加载镜像 images.tar（约 414MB，稍等）"
docker load -i images.tar

# compose 会自动读取本目录的 .env（不要用 --env-file，老版本/部分环境不认这个 flag）
if docker compose version >/dev/null 2>&1; then
  DC="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DC="docker-compose"
else
  echo "错误：未找到 Docker Compose（既无 'docker compose' 插件，也无 'docker-compose'）。请先安装：" >&2
  echo "  sudo apt install -y docker-compose-plugin   # 或  sudo yum install -y docker-compose-plugin" >&2
  exit 1
fi
echo "==> 使用：$DC"

echo "==> [2/3] 启动服务（连外部云 MySQL，自动读取 ./.env）"
$DC up -d

echo "==> [3/3] 状态"
$DC ps

echo ""
echo "完成。浏览器打开：http://<本服务器IP>/"
echo "看后端日志：docker compose logs -f backend"
echo "停止：docker compose down"
