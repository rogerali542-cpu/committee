#!/usr/bin/env bash
# 从 deploy/docker/.env 生成「值已内联」的平台上传用 compose：docker-compose.deploy.yml。
# 用途：资源底座若没有「环境变量」页，就把真实值直接写进 compose 的 environment:，
#       平台原样注入容器（平台不做 ${VAR} 插值，故写字面量正好合适）。
# 产物含真实密钥，已 gitignore —— 只上传平台，切勿提交/外传。
# 用法（任意目录）：  bash deploy/docker/make-deploy-compose.sh
set -e
cd "$(dirname "$0")"                 # 切到 deploy/docker/
ENV_FILE=".env"
OUT="docker-compose.deploy.yml"
[ -f "$ENV_FILE" ] || { echo "缺少 $ENV_FILE（先按 .env.example 复制并填真实值）"; exit 1; }

# 把 .env 一行 KEY=VALUE 输出成缩进 6 空格的  KEY: "VALUE"（转义 \ 和 "，去掉 \r）
emit_env() {                        # $1 可选：只输出这一个 KEY；缺省=全部
  local want="${1:-}"
  grep -vE '^[[:space:]]*#|^[[:space:]]*$' "$ENV_FILE" | while IFS= read -r line; do
    key="${line%%=*}"; val="${line#*=}"
    key="$(echo "$key" | tr -d '[:space:]')"
    [ -n "$want" ] && [ "$key" != "$want" ] && continue
    val="${val%$'\r'}"
    val="${val//\\/\\\\}"; val="${val//\"/\\\"}"
    printf '      %s: "%s"\n' "$key" "$val"
  done
}

{
cat <<'HEAD'
# ⚠ 自动生成、内联了真实密钥、已 gitignore —— 只上传平台，勿提交勿外传。
# 生成/更新：bash deploy/docker/make-deploy-compose.sh（改了 .env 就重跑）
# 平台无「环境变量」页时用它：真实值已写进 environment:，平台原样注入容器。
# 只上传【本文件 + images.tar】；勿传 .env / docker-compose.yml / *.local.yml。
services:
  ocr-asr-service:
    image: ywh-ocr-asr:latest
    environment:
      PORT: "8003"
      TZ: Asia/Shanghai
HEAD
emit_env
cat <<'MID1'
    expose:
      - "8003"

  backend:
    image: ywh-backend:latest
    environment:
      JAVA_OPTS: "-XX:+UseSerialGC -XX:ActiveProcessorCount=1 -Xmx256m -Xms64m -Xss512k"
      OCR_SERVICE_BASE_URL: "http://ocr-asr-service:8003"
      STORAGE_AUDIO_DIR: "/app/data/audio"
      TZ: Asia/Shanghai
MID1
emit_env
cat <<'MID2'
    volumes:
      - backend-audio:/app/data/audio
    expose:
      - "8080"
    depends_on:
      - ocr-asr-service

  web:
    image: ywh-frontend:latest
    environment:
MID2
emit_env OCR_SERVICE_TOKEN
cat <<'TAIL'
    labels:
      sandbox.expose: "true"
    expose:
      - "80"
    depends_on:
      - backend
      - ocr-asr-service

volumes:
  backend-audio:
TAIL
} > "$OUT"

echo "已生成：$(pwd)/$OUT"
echo "上传平台：本文件 + images.tar（勿传 .env / docker-compose.yml / *.local.yml）"
