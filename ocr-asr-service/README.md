# OCR & ASR Service

一个自包含的 AI 能力微服务, 从内部系统拆出来独立分享. 提供三类能力, 全部通过 HTTP/WebSocket 调用, 单一内部令牌鉴权:

- **合同/发票 OCR**: 上传发票图片 -> 结构化票面要素 (金额/税额/购销方等); 也支持整页扫描件 OCR 出全文.
- **语音识别 (ASR)**: 上传音频或实时推流 -> 文字 (火山引擎大模型流式语音识别).
- **会议纪要 (可选)**: 转写片段 -> 结构化纪要 (摘要/结论/风险/待确认) 与待确认行动建议.

> 设计要点: 本服务**无数据库、无状态**. OCR/ASR 都是「字节进、结果出」的纯函数, 识别结果是否落库由调用方自己决定. 所以部署只需要填几个第三方密钥, 不需要建库。

---

## 一、能跑起来需要什么

| 能力 | 依赖的第三方 | 在哪开通 |
|------|-------------|---------|
| OCR (发票/整页) | 火山方舟 ARK 视觉模型 (豆包) | https://console.volcengine.com/ark |
| 会议纪要 | 火山方舟 ARK 文本模型 (豆包) | 同上, 另需一个文本模型 ID |
| 语音识别 ASR | 火山引擎大模型流式语音 (sauc) + 本机 `ffmpeg` | https://console.volcengine.com/speech |

只想用 OCR: 只配 `ARK_API_KEY` 即可, ASR 留 `VOLC_ASR_ENABLED=false`.

---

## 二、目录结构

```
ocr-asr-service/
├── README.md                  # 本文件
├── .env.example               # 环境变量样例 (复制为 .env 填密钥)
├── requirements-core.txt      # Python 依赖
├── Dockerfile                 # 生产镜像 (自带 ffmpeg)
├── entrypoint.sh
├── ocrasr/                    # Python 包 (服务本体)
│   ├── config.py              # 配置 (读 .env / 环境变量)
│   ├── main.py                # FastAPI 应用, 所有 HTTP/WS 端点
│   ├── schemas.py             # 请求/响应模型
│   └── services/
│       ├── ocr_client.py      # 视觉 OCR (发票结构化 + 整页全文)
│       ├── asr_client.py      # 火山流式 ASR (含 ffmpeg 转码、WS 协议)
│       └── llm_client.py      # ARK 文本模型客户端 (会议纪要用)
├── examples/
│   └── client_example.py      # httpx 调用示例
└── frontend-reference/        # 前端样式/交互参考 (见该目录 README)
    ├── InvoiceOcrButton.vue
    ├── AiMeetingRecorder.vue
    └── README.md
```

---

## 三、跑起来

### 方式 A: Docker (推荐, 自带 ffmpeg)

```bash
cp .env.example .env        # 然后编辑 .env 填密钥
docker build -t ocr-asr-service:latest .
docker run -d --name ocr-asr -p 8003:8003 --env-file .env ocr-asr-service:latest
curl http://127.0.0.1:8003/        # 看到 running 即成功
```

### 方式 B: 本机直接跑 (开发调试)

```bash
python -m venv .venv && source .venv/bin/activate    # Windows: .venv\Scripts\activate
pip install -r requirements-core.txt
cp .env.example .env        # 编辑 .env 填密钥
# 用 ASR 时需本机装 ffmpeg (Windows: winget install --id=Gyan.FFmpeg -e)
uvicorn ocrasr.main:app --host 0.0.0.0 --port 8003
```

启动后 Swagger 文档在 http://127.0.0.1:8003/docs

---

## 四、接口一览 (全部需请求头 `X-Internal-Token: <你的 INTERNAL_TOKEN>`)

| 方法 | 路径 | 作用 | 输入 |
|------|------|------|------|
| POST | `/v1/ocr/invoice` | 发票结构化识别 | form-data `file` = 发票图片 (PNG/JPG, <=8MB) |
| POST | `/v1/ocr/image` | 整页 OCR 出全文 | form-data `file` = 单页图片 |
| POST | `/v1/asr/recognize` | 语音识别 (一次性) | form-data `file` = 音频 (webm/wav/mp3...) |
| POST | `/v1/asr/recognize-stream` | 语音识别 (SSE 流式) | 同上, 返回 `text/event-stream` |
| WS | `/v1/asr/stream?token=<令牌>` | 实时流式 ASR | client 推 16k/16bit/mono raw PCM 二进制帧 |
| POST | `/v1/meetings/generate-minutes` | 生成会议纪要 | JSON `{segments:[{text,...}]}` |
| POST | `/v1/meetings/extract-actions` | 提取行动建议 | JSON `{meeting:{}, segments:[...]}` |

> PDF 发票/合同: 本服务只收图片. PDF 请调用方先渲染为单页图片 (如 PyMuPDF/pdf2image, dpi=200) 再逐页调 `/v1/ocr/*`.

调用示例见 `examples/client_example.py`.

---

## 五、安全须知

- **`.env` 含真实密钥, 切勿提交 git** (本目录已带 `.gitignore` 忽略 `.env`).
- `INTERNAL_TOKEN` 是唯一鉴权, 自己随机生成一串够长的字符串; 服务别直接裸暴露公网, 建议放内网或加一层网关.
- OCR/ASR 识别结果可能有误 (尤其金额), **落库前务必人工确认**, 服务本身不写任何库.
