# 业委会后端 Spring Boot 生产镜像（多阶段：Maven 构建 → JRE 运行）
# 在【仓库根目录】构建：
#   docker build --platform linux/amd64 -t ywh-backend:latest -f backend/Dockerfile .
# 首次构建需要联网（下载 Maven 依赖）。jave-all-deps 自带 ffmpeg，运行时无需另装。

# ── 构建阶段 ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# 先只拷 pom 预热依赖缓存层（源码变动时不必重新下载依赖）
COPY backend/pom.xml ./pom.xml
RUN mvn -q -B -DskipTests dependency:go-offline || true
# 再拷源码
COPY backend/src ./src
# 用「纯环境变量驱动、无密钥」的配置覆盖本地 application.yml —— 避免把本地 gitignore
# 配置里的真实密钥打进 jar/镜像。真实密钥运行时由平台环境变量注入。
COPY deploy/docker/application-docker.yml ./src/main/resources/application.yml
RUN mvn -q -B -DskipTests clean package

# ── 运行阶段 ───────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/ywh-backend-*.jar /app/app.jar
COPY deploy/docker/backend-entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh
# 录音落地目录（挂 named volume；local 存储模式用）
RUN mkdir -p /app/data/audio
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
