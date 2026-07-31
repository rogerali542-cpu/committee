#!/usr/bin/env bash
# 用当前仓库源码构建三镜像并导出 images.tar（linux/amd64，连外部云 MySQL 模型）。
# 三镜像：ywh-frontend / ywh-backend / ywh-ocr-asr。产物 images.tar 落本目录。
# 用法（任意目录）：bash deploy/server-bundle/build-images.sh
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"      # deploy/server-bundle
ROOT="$(cd "$HERE/../.." && pwd)"          # 仓库根
cd "$ROOT"
PLAT="linux/amd64"

echo "==[1/4] 构建前端 ywh-frontend（Vite 打包 + nginx 入口反代）=="
docker build --platform "$PLAT" -t ywh-frontend:latest -f deploy/docker/frontend.Dockerfile .

echo "==[2/4] 构建后端 ywh-backend（Maven 打包 → JRE 运行）=="
docker build --platform "$PLAT" -t ywh-backend:latest -f backend/Dockerfile .

echo "==[3/4] 构建语音/OCR ywh-ocr-asr（Python + 静态 ffmpeg）=="
docker build --platform "$PLAT" -t ywh-ocr-asr:latest ocr-asr-service

echo "==[4/4] 导出 images.tar（三镜像合一）=="
docker save ywh-frontend:latest ywh-backend:latest ywh-ocr-asr:latest -o "$HERE/images.tar"

echo "完成：$HERE/images.tar"
ls -lh "$HERE/images.tar"
