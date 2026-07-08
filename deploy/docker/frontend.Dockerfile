# check=skip=SecretsUsedInArgOrEnv
# ↑ OCR_SERVICE_TOKEN 只是给 nginx envsubst 兜底的【空占位】默认值（非真实密钥），真实值运行时注入。故跳过该 lint 误报。
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
# 兜底空值：即使平台没注入 OCR_SERVICE_TOKEN，envsubst 也能把模板里的 ${OCR_SERVICE_TOKEN} 替成空串，
# nginx 不会因"unknown variable"启动失败（仅实时语音鉴权失败→前端自动降级为整段录音）。
# 平台/运行时注入的真实值会覆盖此默认。
ENV OCR_SERVICE_TOKEN=""
EXPOSE 80
