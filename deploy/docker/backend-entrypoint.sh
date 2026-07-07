#!/usr/bin/env bash
# 后端启动前先等 MySQL 起来（平台约束不能用 depends_on 的 condition，故在这里自等）。
set -e

DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"

echo "[entrypoint] 等待 MySQL ${DB_HOST}:${DB_PORT} 就绪 ..."
for i in $(seq 1 60); do
  if (echo > "/dev/tcp/${DB_HOST}/${DB_PORT}") >/dev/null 2>&1; then
    echo "[entrypoint] MySQL 已就绪，启动后端"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "[entrypoint] 等待 MySQL 超时（120s），仍尝试启动（Spring 会再报错）"
  fi
  sleep 2
done

exec java ${JAVA_OPTS:-} -jar /app/app.jar
