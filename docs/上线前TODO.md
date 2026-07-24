# 上线前必办清单

> 测试期为了演示方便留下的临时方案，上线前逐条处理。处理完打勾。

## 待接入的外部接口（等对方就绪，非本项目代码问题）

- [ ] **工单系统对接**：代码已完整（`MeetingTodoTicketService`/`ReceptionTicketService`，
      按 `external-ticket-api.md` 调 `POST /api/external/v1/tickets`）。差对方凭证——
      在 `backend/.env` 填三样并重启即通：`EXTERNAL_TICKET_ENABLED=true`、
      `EXTERNAL_TICKET_UID=<对方分配的UID>`、确认 `EXTERNAL_TICKET_BASE_URL` 与
      `EXTERNAL_TICKET_COMMUNITY_CODE`。⚠ 对方系统侧也要把该 UID 登记并启用（双向配置才通）。
- [ ] **可信档案馆对接**（真正公示的最后一步）：线下会议流程与公示 PDF 均已就绪，
      「确认发布公示」目前只落本系统库。待对方提供档案馆归档/上链接口后，在 `publish()`
      成功后追加一次归档调用（会议记录/纪要/公示三件 + 材料 + 张贴留痕照片），拿回存证号回填。
      需要对方给：接口地址、鉴权方式、归档报文字段规范。

- [x] **转写结果落库**（0721 记，0722 已做）：`MeetingRecording` 加了 `asr_json` 列
      （V32 迁移，ddl-auto 自动建列），识别完成即落库；`resultForRecording` / `result()`
      内存未命中时从库恢复。落库之前识别的旧录音没有该值，前端单段查看提供「重新识别」找回。
- [ ] **首页会议卡片测试期过滤复原**（Committee.vue `loadAll` 内有"上线前复原"注释）：
      恢复 `HISTORY_ONLY_TITLES` 排除逻辑的去留决策；按产品要求决定是否加回
      `compliance !== 'invalid'` 与"已公示不再显示"两个条件。
- [ ] **身份切换改真实登录**：Profile 页写死了全部 8 个演示身份可随意切换（`allIdentities`），
      上线须换成真实账号体系，删掉自由切换。
- [ ] **dev-token 鉴权关闭**：前端无 token 时用 `dev-token-<roleId>` 直通
      （h5/src/api/core.js），上线必须关闭后端对 dev-token 的接受。
- [ ] **代投凭证是否恢复必填**（0722 记）：主持人代委员投票的凭证照片测试期改为选填
      （`CommitteeService.validateProxyRequest` 注释处 + TopicSheet `canSubmitProxy`），
      上线前决定是否恢复必填（防"主任替人乱投"的审计要求）。
- [ ] **会后整理不真正结束会议（0722 记）**：测试期用户要求会议留着手动删，
      MeetingLiveQuick 与 Minutes.vue 各有一个 `TEST_KEEP_MEETING_OPEN=true`，让
      「生成会议纪要/完成会后整理/纪要页确认无误」都跳过 advance('end')，会议保持进行中。
      上线前两处都置回 false 恢复结束归档（公示流程依赖已结束状态）。
- [ ] **超长录音识别预算**（低优先）：单段识别轮询预算封顶 30 分钟
      （MeetingLiveQuick `maxPollCount`），单段录音超约 1.5 小时可能误报超时。
      建议引导分段录音（已是推荐用法），或调高封顶。
- [ ] **/api/auth/dev-roles 关闭**（0723 记）：测试期登录页/个人中心切身份的名单接口，
      免登录暴露委员姓名（SecurityConfig permitAll + AuthController.devRoles）。
      上线换真实登录后必须移除或加鉴权；前端 Login/Profile 的兜底写死名单一并删除。
- [ ] **地图 Key 上线处理**（0723 记）：现用个人开发者测试 Key（h5/.env.local，
      高德 VITE_AMAP_KEY/JSCODE + 腾讯 VITE_TXMAP_KEY，二选一 VITE_MAP_PROVIDER）。
      测试免费合规，正式上线运营需办三件事：
      ① 账号做**企业认证**（免费，提额度 + 合规；视用量评估是否办高德技术服务许可/腾讯商业授权，
         小区级通常最低档或走政务/公益免费通道）；
      ② **域名白名单锁死**到正式域名（高德不带端口 / 腾讯带端口），防 Key 被盗刷；
      ③ **高德安全密钥（jscode）挪到后端 nginx 代理**（现明文写在前端，上线有暴露风险；
         腾讯是 iframe 组件无此问题）。
