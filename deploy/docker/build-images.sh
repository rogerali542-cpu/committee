#!/usr/bin/env bash
# 一键构建三个镜像并导出 images.tar（供资源底座沙箱「快速部署」上传）。
# 数据库连【外部云 MySQL】（见 docker-compose.yml），不打包 DB 镜像。
# 前置：本机已装 Docker 并启动。首次构建需联网。
# 用法（任意目录）：  bash deploy/docker/build-images.sh
set -e
PLATFORM=linux/amd64

# 定位仓库根（本脚本在 deploy/docker/ 下）
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"
echo "仓库根：$REPO_ROOT"

echo "==> [1/4] 构建后端 ywh-backend"
docker build --platform $PLATFORM -t ywh-backend:latest -f backend/Dockerfile .

echo "==> [2/4] 构建语音服务 ywh-ocr-asr"
docker build --platform $PLATFORM -t ywh-ocr-asr:latest -f ocr-asr-service/Dockerfile ocr-asr-service

echo "==> [3/4] 构建前端/入口 ywh-frontend"
docker build --platform $PLATFORM -t ywh-frontend:latest -f deploy/docker/frontend.Dockerfile .

echo "==> [4/4] 导出 images.tar"
docker save ywh-backend:latest ywh-ocr-asr:latest ywh-frontend:latest -o deploy/docker/images.tar

echo "完成：deploy/docker/images.tar（$(du -h deploy/docker/images.tar | cut -f1)）"
echo "下一步：见 deploy/README-沙箱Docker部署.md"
