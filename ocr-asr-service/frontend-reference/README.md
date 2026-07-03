# 前端参考组件

这两个 Vue 3 (Composition API + TypeScript + Tailwind) 组件是从内部系统原样拷出来的, **作为样式与交互参考**, 不保证拖进新项目就能直接跑 (它们依赖原系统的若干公共设施, 见下). 拿去抄布局、抄交互流程、抄 Tailwind class, 然后按你自己项目的 API 层适配.

## 1. InvoiceOcrButton.vue  (基本可复用)

发票 OCR 上传按钮: 选图片 -> 调后端识别 -> `emit('recognized', 结果)`. 父组件拿到结果预填表单, 用户确认才落库.

需要适配的依赖:
- `@/api/finProjectFinance` 的 `recognizeInvoiceOcr(file)` 和 `InvoiceOcrResult` 类型 —— 换成你自己封装的「调 `/v1/ocr/invoice` 并把响应整理成 `{ok, fields...}`」的函数.
- `@/utils/message` —— 轻提示封装 (success/error/warning), 换成你项目里的 toast 即可.

适配工作量: 小. 把那两个 import 换掉就能用.

## 2. AiMeetingRecorder.vue  (深度耦合, 仅作交互参考)

会中控制台: 录音 + 实时转写流 + 会议设置 (小组/项目上下文/材料上传) + 会后跳确认台. 是一条完整业务流, **不是独立组件**.

强耦合的原系统设施 (拿走前都要替换):
- `@/api/aiMeetings` (createAiMeeting / startAiMeeting / streamAiMeetingTranscribe / uploadAiMeetingAudio ...) —— 整套会议生命周期接口, 后端在原系统里, 本分享包不含.
- `@/api/squad`, `@/api/projectPlan` —— 小组、项目计划上下文, 原系统业务.
- `vue-router` 的命名路由 `AiMeetingConfirm` —— 跳转目标页, 本包不含.
- `@/utils/confirm`, `@/utils/message` —— 确认弹窗 / 轻提示.
- 浏览器 `SpeechRecognition` (采集授权语音校验) 与 `MediaRecorder`/AudioWorklet (录音, 在 `streamAiMeetingTranscribe` 里).

怎么用它: 看 `<template>` 学三栏布局 (会议设置 / 实时转写流 / AI 小助手) 和录音状态机 (准备 -> 录音中 -> 处理中) 的交互, 把「实时转写」那段接到本服务的 `WS /v1/asr/stream` 或 `POST /v1/asr/recognize-stream`. 其余会议/项目上下文是原系统业务, 按需裁掉.

## 公共约定
- 原系统前端请求基址是 `/Nexus/v1` (`@/api/request` 里配的), 你的项目按自己的来.
- 配色主基调: 青色 (`cyan-600/700`) 作主操作色, 灰阶 (`gray-*`) 作中性色, 状态用 `emerald/rose/amber/sky`.
