# 沙箱自带 MySQL：烘入种子数据（首次启动、数据卷为空时自动导入）。
# 构建（在 deploy/docker/ 下）：docker build -f mysql.Dockerfile -t ywh-mysql:latest .
FROM mysql:8.0
COPY db-seed/ywh_db_full.sql /docker-entrypoint-initdb.d/10-ywh-db-full.sql
