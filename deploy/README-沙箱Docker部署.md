# 业委会 demo —— 资源底座沙箱 Docker 部署

> 目标：把这套 **前端(Vue) + 后端(Spring Boot/Java) + 语音服务(Python)** 打包成镜像（数据库用外部云 MySQL，不打包），
> 上传到资源底座「开发者中心 → 快速部署」，平台自动分配域名。
>
> 全套文件已备好（本目录 `deploy/docker/`）。**只差一步「有 Docker 的机器构建镜像」**——
> 本开发机当前没装 Docker，构建那一步等你把 Docker 环境准备好再做。

---

## 一、架构与端口

```
                         平台分配的 https 域名
                                 │
                     ┌───────────▼───────────┐
                     │  web (nginx, 入口:80)  │  ← sandbox.expose:"true"
                     │  静态前端 + 反代        │
                     └──┬─────────┬───────────┘
              /api/ →   │         │  /asr-ws →（WS）
              ┌─────────▼──┐   ┌──▼───────────────┐
              │ backend    │   │ ocr-asr-service  │
              │ Java :8080 │   │ Python :8003     │
              └─────┬──────┘   └──────────────────┘
                    │ jdbc
              ┌─────▼───────────────┐
              │ 外部云 MySQL（不打包） │
              └─────────────────────┘
```

三个镜像：`ywh-frontend`（入口）、`ywh-backend`、`ywh-ocr-asr`。数据库连【外部云 MySQL】，不打包 DB 镜像。
前端调相对路径 `/api`、`/asr-ws`（同源），由入口 nginx 反代，**前端代码无需改动**。

---

## 二、本目录文件清单

| 文件 | 作用 |
|---|---|
| `docker-compose.yml` | 平台部署编排（**上传这个**） |
| `docker-compose.local.yml` | 仅本地验证用的端口映射 override（**不要上传**） |
| `.env.example` | 环境变量模板，复制成 `.env` 填真实密钥（**不入库、不上传，值填到平台「环境变量」**） |
| `application-docker.yml` | 后端容器专用配置（纯环境变量驱动、无密钥，会覆盖打进 jar） |
| `nginx-entry.conf.template` | 入口反代配置（令牌运行时注入） |
| `build-images.ps1` / `build-images.sh` | 一键构建三镜像 + 导出 `images.tar` |
| `make-deploy-compose.sh` | 平台【无环境变量页】时用：从 `.env` 生成内联真实值的 `docker-compose.deploy.yml` |
| `docker-compose.deploy.yml` | 上面的生成物：内联了真实密钥的上传编排（**已 gitignore；只上传平台，勿提交/外传**） |
| `../backend/Dockerfile` | 后端多阶段镜像 |
| `frontend.Dockerfile` | 前端 + 入口 nginx 镜像 |
| `../ocr-asr-service/Dockerfile` | 语音/OCR 镜像（原有） |

---

## 三、部署步骤

### 步骤 0：装 Docker（本机当前没有）
装 **Docker Desktop**（Windows 需 WSL2，要管理员权限 + 重启一次）。
装好后 `docker version` 能看到 Server 版本即可。
> 没有 Docker 就无法构建/导出镜像——这是 Docker 路线的硬前置。

### 步骤 1：填密钥
```bash
cp deploy/docker/.env.example deploy/docker/.env
# 编辑 deploy/docker/.env，按注释从本地 gitignore 配置里拷真实值：
#   豆包 ASR/LLM → backend/src/main/resources/application.yml
#   ARK/VOLC     → ocr-asr-service/.env
#   OCR_SERVICE_TOKEN / MYSQL_ROOT_PASSWORD / JWT_SECRET → 自己定
```

### 步骤 2：一键构建镜像
```powershell
# Windows PowerShell（仓库根目录）
.\deploy\docker\build-images.ps1
```
```bash
# 或 bash
bash deploy/docker/build-images.sh
```
产出：`deploy/docker/images.tar`（三镜像，约 **400–500 MB**；含 java+python+ffmpeg，不含 DB 镜像）。

### 步骤 3：本地先验证（强烈建议，省得上传后才发现问题）
```bash
# 进 deploy/docker 目录跑，保证 .local.yml 里的 env_file: .env 路径解析正确
cd deploy/docker
docker compose -f docker-compose.yml -f docker-compose.local.yml up
```
> 本地用 `docker-compose.local.yml` 的 `env_file` 从 `.env`【直接注入】环境变量，
> 与平台注入方式一致——本地能过，平台大概率也能过。

浏览器开 `http://localhost:8088`：
- 能进登录页、选身份、进主页 → 前端 + 后端 + MySQL 通
- 议题里点「语音输入」说话出字 → 实时语音（WS）通
- 上传图片材料能 OCR、能生成纪要 → 语音/OCR 服务 + 豆包凭证通

没问题后 `Ctrl+C` 停掉，进入上传。

### 步骤 4：上传平台

先看平台「快速部署」页**有没有「环境变量」录入框**，二选一：

**情况 A：有「环境变量」页**
1. 「开发者中心 → 快速部署」，选 **Docker 部署**。
2. 上传 `deploy/docker/images.tar` 和 `deploy/docker/docker-compose.yml`。
3. 在平台「环境变量」里，把 `deploy/docker/.env` 里的**每一项都填进去**（不只是密钥！）。
   ⚠ 平台是把这些变量【直接注入容器】、**不做 compose 的 `${VAR}` 插值**——所以
   `docker-compose.yml` 里已不再写 `${VAR}`，全靠这里注入。凡是 `.env` 里列出的
   （`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`、`JWT_SECRET`、`OCR_SERVICE_TOKEN`、
   `DOUBAO_*`、`ARK_*`、`VOLC_ASR_*`、`STORAGE_*`）**都要填**，漏填的按应用内默认值走。
4. 点「立即上线」→ 等审核通过 → 平台分配访问域名。

**情况 B：没有「环境变量」页**（把真实值内联进编排，平台原样注入容器）
1. 生成内联了真实值的上传编排（读 `.env` → 产出 `docker-compose.deploy.yml`）：
   ```bash
   bash deploy/docker/make-deploy-compose.sh
   ```
2. 上传 `deploy/docker/images.tar` 和 **`deploy/docker/docker-compose.deploy.yml`**（注意：**不是** `docker-compose.yml`）。
3. 点「立即上线」。
> `docker-compose.deploy.yml` 内联了真实密钥、已 gitignore——**只上传平台，勿提交/外传**；改了 `.env` 就重跑生成脚本覆盖它。
> 它是**平台专用**，别拿它在本地 `docker compose up`（本地验证仍走步骤 3 的 `.local.yml`；本地真 compose 会把值里的 `$` 当插值，平台则不会）。

---

## 四、务必注意的点

- **平台注入模型（最容易踩）**：资源底座把「环境变量」页的 KEY=VALUE **直接注入容器**，
  **不对 `docker-compose.yml` 的 `${VAR}` / `${VAR:-default}` 做插值**。若编排里写 `DB_HOST: "${DB_HOST}"`，
  容器里拿到的是字面量 `${DB_HOST}`——后端会报 `Circular placeholder reference`、nginx 会报 `unknown variable` 直接崩。
  故本套编排的 `environment:` 只留静态值，其余全靠平台注入；本地用 `.local.yml` 的 `env_file` 同样直接注入以保持一致。
- **密钥不进镜像/不进仓库**：都走平台「环境变量」注入。`application-docker.yml` 已清空所有密钥默认值；`.dockerignore` 也排除了 `.env`。
- **WebSocket（实时语音）**：入口 `/asr-ws` 是 WebSocket。**需确认平台入口支持 wss 转发**——若平台不支持 WS，实时语音会连不上（此时前端会自动降级为「整段录音→识别」，功能仍可用，只是不是边说边出字）。这条建议上线后第一时间验证。
- **单入口**：平台只把域名指向带 `sandbox.expose:"true"` 的 `web` 服务，其余走内网服务名互访（backend / ocr-asr-service），已配好。
- **数据持久化**：会议数据存在【外部云 MySQL】，沙箱重启不会丢（空库时后端 `ddl-auto=update` 会自动建表）。容器内只有 `backend-audio`（本地音频落盘）用 named volume，沙箱重启会清空；长录音已走 TOS 对象存储（`STORAGE_AUDIO_TYPE=tos`）不受此影响。
- **AI 走的是直连豆包/火山**（不是资源底座的 `tlk_` 网关）：需要**沙箱容器能出公网**访问 `ark.cn-beijing.volces.com` 和 `openspeech.bytedance.com`。若沙箱禁外网，AI 功能不可用，需改造成走资源底座网关（实时语音的 VOLC WebSocket 私有协议网关未必兼容，需另议）。
- **镜像体积**：`images.tar` 约 400–500 MB（三镜像、不含 DB）。

---

## 五、备选：平台 ECS 云服务器

如果沙箱的 WS/外网/持久化限制太多，改用平台的 **云服务器（ECS）** 更省心：一台真 Linux VM，
直接跑 `deploy/nginx-h5.conf` + Java + MySQL + Python 那套（见 `deploy/README-部署.md`）。
付费（轻量版约 183 元/月），但最贴合现有技术栈、无沙箱各种约束。
