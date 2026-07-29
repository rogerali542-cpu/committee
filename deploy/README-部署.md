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

## 五、外部工单系统对接（会议待办 / 接待诉求 → communityHub）

业委会这边把「会议待办」和「接待诉求」推送到**社区智能运维协同平台**（communityHub）建单。
代码已全接好（前端「派发工单」按钮 → 后端 `ReceptionTicketService` / `MeetingTodoTicketService`
→ `POST {base-url}/api/external/v1/tickets`，带头 `X-External-Uid`），**默认关闭，靠配置开**。
接口规范见仓库根目录 `external-ticket-api.md`。

### 5.1 启用配置（4 项，改完必须重启后端）

在服务器环境变量 / 平台密钥注入里设置（或用 `env_file` 变体时写进 `deploy/docker/.env`）：

```bash
EXTERNAL_TICKET_ENABLED=true
EXTERNAL_TICKET_BASE_URL=https://communityhub.slaicreativity.com   # 若对方 API 在 /communityHub 上下文下，则写成 .../communityHub
EXTERNAL_TICKET_UID=<你的外部系统UID>                                # 凭证：找运维 / 在 communityHub 后台取，切勿入库
EXTERNAL_TICKET_COMMUNITY_CODE=<你的小区编码>                        # 如 COMM_XX
```

- **UID / 小区编码是访问凭证，不要提交进 git**：仓库已 gitignore 掉真实 `.env`（`deploy/**/.env`），只填服务器。
- 首次启用重启后端时，`ddl-auto:update` 会自动补 `external_ticket_no / ticket_no / ticket_pushed_at` 列，无需手动建。
- 其余 `EXTERNAL_TICKET_REPORTER_NAME / _TYPE / _RISK_LEVEL / _PRIORITY` 有默认值，可不填。

### 5.2 自测（在能访问 communityHub 的机器上跑；把 `<UID>` / `<小区编码>` 换成实际值）

**① 探路（不建单，安全）**——先确认路径和凭证对不对：

```bash
curl -i -X POST 'https://communityhub.slaicreativity.com/api/external/v1/tickets' \
  -H 'Content-Type: application/json;charset=UTF-8' \
  -H 'X-External-Uid: <UID>' \
  -d '{"communityCode":"<小区编码>"}'
```

结果解读：
- **400**（提示缺 title / externalTicketNo 等）→ 路径对、UID 有效、小区在白名单 ✅ 可启用
- **404** → 路径不对：base-url 改成 `https://communityhub.slaicreativity.com/communityHub` 再试
- **401** → UID 未登记 / 未启用
- **403** → UID 被禁用，或该小区不在此 UID 的白名单里

**② 真建单（会在对方产生 1 条测试单，幂等，可重复跑不重复建）**：

```bash
curl -i -X POST 'https://communityhub.slaicreativity.com/api/external/v1/tickets' \
  -H 'Content-Type: application/json;charset=UTF-8' \
  -H 'X-External-Uid: <UID>' \
  -d '{"externalTicketNo":"YWH-SELFTEST-001","communityCode":"<小区编码>","title":"业委会对接自测","location":"测试","reporterName":"业委会"}'
```

返回 `code:200` + `data.ticket.ticketNo` 即打通；登录 communityHub 后台（管理员账号）可见该单。

### 5.3 App 里验证

启用并重启后端后，进「业主接待」某条记录点「派发工单」（或会议待办点派单）——
成功后卡片显示对方单号（`ticketNo`）；失败会提示「工单系统连接失败 / 尚未启用 / 配置不完整」，据此对照 5.1、5.2 排查。

## 六、等你给服务器地址后我能补的

给我：**域名 + dist 部署路径 + 后端是否同机(不同机给 IP) + 证书路径**，我把 `nginx-h5.conf` 里的占位直接填好给你，并按你服务器情况（同机/分离、有无 docker）给到更具体的启动命令。
