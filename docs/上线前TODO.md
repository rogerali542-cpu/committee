# 上线前必办清单

> 测试期为了演示方便留下的临时方案，上线前逐条处理。处理完打勾。

- [ ] **转写结果落库**（0721 记）：各段 ASR 转写目前只存在后端内存
      （`DoubaoAsrService.recordingResults`），后端一重启就全丢，需要重新识别、重复花豆包识别费。
      方案：`MeetingRecording` 加转写文本字段（或独立表），识别完成时落库；
      `resultForRecording` / `result()` 优先读库，内存作缓存。
- [ ] **首页会议卡片测试期过滤复原**（Committee.vue `loadAll` 内有"上线前复原"注释）：
      恢复 `HISTORY_ONLY_TITLES` 排除逻辑的去留决策；按产品要求决定是否加回
      `compliance !== 'invalid'` 与"已公示不再显示"两个条件。
- [ ] **身份切换改真实登录**：Profile 页写死了全部 8 个演示身份可随意切换（`allIdentities`），
      上线须换成真实账号体系，删掉自由切换。
- [ ] **dev-token 鉴权关闭**：前端无 token 时用 `dev-token-<roleId>` 直通
      （h5/src/api/core.js），上线必须关闭后端对 dev-token 的接受。
- [ ] **超长录音识别预算**（低优先）：单段识别轮询预算封顶 30 分钟
      （MeetingLiveQuick `maxPollCount`），单段录音超约 1.5 小时可能误报超时。
      建议引导分段录音（已是推荐用法），或调高封顶。
