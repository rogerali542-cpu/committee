# 业委会 H5 —— 沙箱部署清单（方案B）

> 目标：把 H5 网页部署到一台公网 + HTTPS 的服务器，微信里打开测试。
> 前端纯静态，调 `/api` 相对路径，由 nginx 同源反代到后端 —— **前端不用改代码、不用重新构建**。

---

## 一、已就绪（本地已完成）

- [x] 前端已构建：`h5/dist/`（`cd h5 && npm run build` 产出，纯静态）
- [x] nginx 配置模板：`deploy/nginx-h5.conf`（含 SPA 回退 + /api 反代 + 大上传 + 长超时）

## 二、服务器需要跑起来的三样东西

| 服务 | 端口 | 说明 |
|---|---|---|
| **nginx** | 443 | 服务 `dist/` 静态 + 反代 `/api` → 后端 |
| **Spring Boot 后端** | 8080 | `backend/`，需 Java 17；配置见 `backend/src/main/resources/application.yml`（含豆包密钥，**不入库，要单独放到服务器**） |
| **OCR/ASR 服务** | 8003 | `ocr-asr-service/`（FastAPI，Python）。材料 OCR 用；密钥在其 `.env`（不入库） |
| **MySQL** | 3306 | 后端连的库 `ywh_db`；JPA `ddl-auto=update` 会自动建表 |

> 沙箱最简：这三样都跑在同一台服务器，nginx 里 `/api` 反代 `127.0.0.1:8080` 即可。

## 三、部署步骤（拿到服务器后）

1. **传前端**：把本地 `h5/dist/` 整个上传到服务器，例如 `/var/www/ywh-h5/dist`。
2. **配 nginx**：用 `deploy/nginx-h5.conf`，把里面 4 个 `__占位__` 换成真实值：
   - `__你的域名__`（两处）
   - `__/path/to/fullchain.pem__` / `__/path/to/privkey.pem__`（SSL 证书）
   - `__/var/www/ywh-h5/dist__`（两处，dist 实际路径）
   - 后端不在同机的话，把 `proxy_pass http://127.0.0.1:8080` 改成实际 `IP:8080`
   然后 `nginx -t` 校验 → `nginx -s reload`。
3. **起后端**：服务器装 Java 17，放好含真实密钥的 `application.yml`，`cd backend && ./mvnw spring-boot:run`（或打成 jar 跑）。确认 `:8080` 起来。
4. **起 OCR**：`cd ocr-asr-service`，放好 `.env`，起 uvicorn（见其 README / Dockerfile）。确认 `:8003`。
5. **微信里打开** `https://你的域名/` 测试。

## 四、几个必须注意的点

- **必须 HTTPS**：微信里录音、拍照需要安全上下文，http 会被拦。证书用 Let's Encrypt 或你们已有的。
- **长超时已在 nginx 配好**：AI 润色最长 ~210s、OCR ~120s，nginx 默认 60s 会掐断，配置里已放到 300s。
- **上传体积**：录音文件大，`client_max_body_size 210m` 已配（对齐后端 200MB 上限）。
- **密钥不入库**：`backend/.../application.yml` 和 `ocr-asr-service/.env` 里的豆包密钥是本地 gitignore 的，**不在代码库里**，需要你手动放到服务器（或用环境变量注入）。
- **音频存储**：目前 `storage.audio.type=local`（豆包走 Base64 内联,不依赖公网音频 URL),沙箱直接可用；等 TOS 就绪再切。
- **微信域名白名单**（如用到 JS-SDK）：普通链接打开一般没问题；若报域名不合法，去公众平台把该域名加进「业务域名 / 安全域名」。

## 五、等你给服务器地址后我能补的

给我：**域名 + dist 部署路径 + 后端是否同机(不同机给 IP) + 证书路径**，我把 `nginx-h5.conf` 里的占位直接填好给你，并按你服务器情况（同机/分离、有无 docker）给到更具体的启动命令。
