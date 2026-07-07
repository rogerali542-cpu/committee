# 业委会前端（Vue3/Vite）+ 入口反代 nginx —— 同时是整个沙箱编排的【入口服务】。
# 在【仓库根目录】构建：
#   docker build --platform linux/amd64 -t ywh-frontend:latest -f deploy/docker/frontend.Dockerfile .
# 前端调相对路径 /api、/asr-ws（同源），由本 nginx 反代到后端与语音服务，无需改前端代码。

# ── 构建阶段：Node 打包静态 dist ──────────────────────────
FROM node:20-alpine AS build
WORKDIR /app
COPY h5/package.json h5/package-lock.json ./
RUN npm ci
COPY h5/ ./
RUN npm run build

# ── 运行阶段：nginx 服务 dist + 反代 ──────────────────────
FROM nginx:1.27-alpine
# 反代模板：容器启动时官方镜像会对 /etc/nginx/templates/*.template 做 envsubst 生成
# /etc/nginx/conf.d/default.conf。NGINX_ENVSUBST_FILTER=^OCR_ 限定只替换 OCR_* 变量，
# 不动 nginx 自己的 $host / $http_upgrade 等 —— 这样 OCR 内网令牌运行时注入、不烤进镜像。
COPY deploy/docker/nginx-entry.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV NGINX_ENVSUBST_FILTER=^OCR_
EXPOSE 80
