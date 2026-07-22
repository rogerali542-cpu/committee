# 上线前必办清单

> 测试期为了演示方便留下的临时方案，上线前逐条处理。处理完打勾。

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
      MeetingLiveQuick `TEST_KEEP_MEETING_OPEN=true` 让「生成会议纪要/完成会后整理」跳过
      advance('end')，会议保持进行中。上线前置回 false 恢复结束归档（公示流程依赖已结束状态）。
- [ ] **超长录音识别预算**（低优先）：单段识别轮询预算封顶 30 分钟
      （MeetingLiveQuick `maxPollCount`），单段录音超约 1.5 小时可能误报超时。
      建议引导分段录音（已是推荐用法），或调高封顶。
