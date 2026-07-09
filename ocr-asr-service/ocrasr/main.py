import asyncio
import hmac
import json
import time
from typing import Any

from fastapi import (
    FastAPI,
    Depends,
    HTTPException,
    Header,
    File,
    UploadFile,
    WebSocket,
    WebSocketDisconnect,
)
from fastapi.responses import StreamingResponse
from loguru import logger

from ocrasr.config import settings
from ocrasr.schemas import (
    AsrRecognizeResponse,
    MeetingActionExtractRequest,
    MeetingActionExtractResponse,
    MeetingMinutesRequest,
    MeetingMinutesResponse,
    OcrDocumentResponse,
    OcrImageResponse,
    OcrInvoiceResponse,
    OpinionAssistRequest,
    OpinionAssistResponse,
)
from ocrasr.services.llm_client import llm_client

app = FastAPI(
    title="OCR & ASR Service",
    description="合同/发票 OCR 识别 + 大模型流式语音识别 (ASR) + 会议纪要, 单一内部令牌鉴权.",
    version="1.0.0",
)


async def verify_internal_token(x_internal_token: str = Header(...)):
    # 内部接口统一使用共享令牌校验, 用 hmac.compare_digest 做时间安全比较, 防 timing attack.
    if not hmac.compare_digest(x_internal_token or "", settings.INTERNAL_TOKEN):
        logger.warning("Invalid internal token attempt (token value not logged)")
        raise HTTPException(status_code=403, detail="Invalid internal token")


@app.get("/")
async def root():
    return {
        "message": "OCR & ASR Service is running",
        "ocr_model": settings.OCR_VISION_MODEL_ID,
        "asr_enabled": settings.VOLC_ASR_ENABLED,
    }


# ============ 会议纪要与行动建议 ============

_MEETING_MINUTES_SYSTEM_PROMPT = """你是项目会议纪要助手. 根据会议转写片段生成结构化会议纪要.

只输出 JSON 对象, 不输出 markdown 或额外解释. JSON schema:
{
  "summary": "会议摘要, 120 字内",
  "decisions": ["关键结论, 每条 60 字内"],
  "risks": ["风险或阻塞, 每条 60 字内"],
  "open_questions": ["待确认问题, 每条 60 字内"]
}

要求:
1. 不编造转写片段里没有的信息.
2. 内容为空时返回空字符串或空数组.
3. 相同意思的句子合并, 不要逐句机械罗列.
"""

_MEETING_ACTIONS_SYSTEM_PROMPT = """你是项目管理会议行动建议提取助手. 根据会议转写片段提取可人工确认的行动建议.

只输出 JSON 对象, 不输出 markdown 或额外解释. JSON schema:
{
  "actions": [
    {
      "action_type": "node_create | node_update | milestone_update | notification_send",
      "target_project_id": 1,
      "target_project_plan_id": 1,
      "target_milestone_id": 1,
      "target_node_id": 1,
      "assignee_user_id": 1,
      "payload": {
        "title": "动作标题",
        "description": "动作说明",
        "start_date": "YYYY-MM-DD",
        "end_date": "YYYY-MM-DD"
      },
      "source_segment_ids": [1],
      "confidence": 0.8
    }
  ]
}

规则:
1. 只能提取和项目计划、里程碑、节点、通知有关的建议.
2. 没有明确目标时也可以输出, 但不要编造目标 ID.
3. source_segment_ids 必须来自输入片段 id.
4. payload.title 必须简短明确, 适合人工确认后下发.
"""


def _meeting_payload_text(payload: dict) -> str:
    return json.dumps(payload, ensure_ascii=False, indent=2)


def _token_usage(raw: dict) -> dict[str, int | None]:
    usage = raw.get("usage") or {}
    return {
        "input": usage.get("prompt_tokens"),
        "output": usage.get("completion_tokens"),
    }


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


@app.post(
    "/v1/meetings/generate-minutes",
    response_model=MeetingMinutesResponse,
    dependencies=[Depends(verify_internal_token)],
)
async def generate_meeting_minutes_endpoint(payload: MeetingMinutesRequest):
    """基于转写片段生成结构化纪要."""
    segments = [item.model_dump() for item in payload.segments if item.text.strip()]
    if not segments:
        raise HTTPException(status_code=400, detail="segments 不能为空")

    raw = await llm_client.chat_completion(
        system_prompt=_MEETING_MINUTES_SYSTEM_PROMPT,
        user_prompt=_meeting_payload_text({"segments": segments}),
        response_model=False,
    )
    result = raw.get("result") or {}
    return MeetingMinutesResponse(
        summary=str(result.get("summary") or "").strip(),
        decisions=_string_list(result.get("decisions")),
        risks=_string_list(result.get("risks")),
        open_questions=_string_list(result.get("open_questions")),
        tokens=_token_usage(raw),
    )


@app.post(
    "/v1/meetings/extract-actions",
    response_model=MeetingActionExtractResponse,
    dependencies=[Depends(verify_internal_token)],
)
async def extract_meeting_actions_endpoint(payload: MeetingActionExtractRequest):
    """基于转写片段提取待确认行动建议."""
    segments = [item.model_dump() for item in payload.segments if item.text.strip()]
    if not segments:
        raise HTTPException(status_code=400, detail="segments 不能为空")

    raw = await llm_client.chat_completion(
        system_prompt=_MEETING_ACTIONS_SYSTEM_PROMPT,
        user_prompt=_meeting_payload_text({
            "meeting": payload.meeting,
            "segments": segments,
        }),
        response_model=False,
    )
    result = raw.get("result") or {}
    actions = result.get("actions")
    return MeetingActionExtractResponse(
        actions=actions if isinstance(actions, list) else [],
        tokens=_token_usage(raw),
    )


# ============ 议题意见 AI 助手 ============
# 业委会委员多为老年人, 口语表达居多: polish 把已有意见改得正式规范; draft 根据口头描述代拟发言.
# 产出只回给前端供委员确认/修改, 本服务不写任何库.

_OPINION_POLISH_SYSTEM_PROMPT = """你是业主委员会的文书助手. 把委员对某个议题的口语化意见改写成正式、简洁的书面意见.

只输出 JSON 对象, 不输出 markdown 或额外解释: {"text": "改写后的意见"}

要求:
1. 忠实保留原意和立场(同意/反对/建议/疑问), 不添加原文没有的观点、数据和事实.
2. 开门见山, 第一句就是观点; 格式就是一段连贯的话——不加称呼、开场白、结尾客套
   (如"尊敬的各位委员""大家好""谢谢大家""以上是我的意见"), 不用序号分点, 不加标题.
3. 语气正式得体, 第一人称, 用词平实.
4. 简短: 长度与原文相当, 最多不超过 100 字; 能一句话说清就一句话.
5. 原文已经很规范时只做微调, 不硬改.
"""

_OPINION_DRAFT_SYSTEM_PROMPT = """你是业主委员会的文书助手. 委员不太会组织语言, 他口头描述了想对某个议题表达的想法, 请替他起草一条正式、简短的会议意见.

只输出 JSON 对象, 不输出 markdown 或额外解释: {"text": "起草的意见"}

要求:
1. 只依据他描述的意思起草, 不编造他没提到的观点、数据和事实.
2. 立场以他的描述为准(同意/反对/建议/疑问); 描述含糊时用中性、建议性的表述.
3. 开门见山, 第一句就是观点; 格式就是一段连贯的话——不加称呼、开场白、结尾客套
   (如"尊敬的各位委员""大家好""谢谢大家""以上是我的意见"), 不用序号分点, 不加标题.
4. 语气正式得体, 第一人称, 用词平实.
5. 简短: 40~100 字, 能一句话说清就一句话.
"""


@app.post(
    "/v1/opinions/assist",
    response_model=OpinionAssistResponse,
    dependencies=[Depends(verify_internal_token)],
)
async def opinion_assist_endpoint(payload: OpinionAssistRequest):
    """意见润色 (mode=polish) / 发言代拟 (mode=draft)."""
    text = (payload.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text 不能为空")

    system = _OPINION_DRAFT_SYSTEM_PROMPT if payload.mode == "draft" else _OPINION_POLISH_SYSTEM_PROMPT
    raw = await llm_client.chat_completion(
        system_prompt=system,
        user_prompt=_meeting_payload_text({
            "topic": {"title": payload.topic_title, "type": payload.topic_type},
            "speaker_role": payload.speaker_role,
            "text": text,
        }),
        response_model=False,
    )
    result = raw.get("result") or {}
    out = str(result.get("text") or "").strip()
    if not out:
        raise HTTPException(status_code=500, detail="AI 未返回有效内容")
    return OpinionAssistResponse(text=out, tokens=_token_usage(raw))


# ============ 语音识别 (ASR) ============
# 接收浏览器上传的音频 (webm/opus/wav/mp3), 用 ffmpeg 转 16k mono PCM, 调火山 ASR 拿文本.

@app.post("/v1/asr/recognize", response_model=AsrRecognizeResponse, dependencies=[Depends(verify_internal_token)])
async def asr_recognize_endpoint(file: UploadFile = File(...)):
    """语音识别 (同步版): webm/opus/wav 等 -> 文字, 等全部识别完成才返回."""
    from ocrasr.services.asr_client import (
        recognize as _asr_recognize,
        AsrError,
        AsrConfigError,
    )

    audio_bytes = await file.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="音频文件为空")

    final = None
    try:
        async for event in _asr_recognize(audio_bytes, mime=file.content_type or ""):
            if event.get("type") == "final":
                final = event
                break
    except AsrConfigError as e:
        raise HTTPException(status_code=503, detail=f"ASR 服务未配置: {e!s}")
    except AsrError as e:
        raise HTTPException(status_code=500, detail=f"语音识别失败 [{e.code}]: {e.message}")

    if not final:
        raise HTTPException(status_code=500, detail="语音识别失败: 未收到 final 事件")

    return AsrRecognizeResponse(
        text=final.get("text", ""),
        utterances=final.get("utterances", []),
        duration_ms=final.get("duration_ms", 0),
        request_id=final.get("request_id", ""),
        log_id=final.get("log_id", ""),
    )


@app.post("/v1/asr/recognize-stream", dependencies=[Depends(verify_internal_token)])
async def asr_recognize_stream_endpoint(file: UploadFile = File(...)):
    """语音识别 (流式版): SSE 流, 实时推送部分识别结果.

    事件类型:
      event: partial   data: {"text": "...", "duration_ms": N, "is_last": false}
      event: final     data: {"text": "...", "utterances": [...], "duration_ms": N, "log_id": "..."}
      event: error     data: {"error": "...", "code": N}
    """
    from ocrasr.services.asr_client import (
        recognize as _asr_recognize,
        AsrError,
        AsrConfigError,
    )

    audio_bytes = await file.read()
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="音频文件为空")

    async def event_stream():
        try:
            async for event in _asr_recognize(audio_bytes, mime=file.content_type or ""):
                ev_type = event.get("type", "partial")
                data_json = json.dumps(event, ensure_ascii=False)
                yield f"event: {ev_type}\ndata: {data_json}\n\n".encode("utf-8")
        except AsrConfigError as e:
            err = {"error": f"ASR 服务未配置: {e!s}", "code": 503}
            yield f"event: error\ndata: {json.dumps(err, ensure_ascii=False)}\n\n".encode("utf-8")
        except AsrError as e:
            err = {"error": f"语音识别失败: {e.message}", "code": e.code}
            yield f"event: error\ndata: {json.dumps(err, ensure_ascii=False)}\n\n".encode("utf-8")
        except Exception as e:  # noqa: BLE001
            err = {"error": f"内部错误: {e!s}", "code": -500}
            yield f"event: error\ndata: {json.dumps(err, ensure_ascii=False)}\n\n".encode("utf-8")

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",  # nginx 不缓冲, 保证流式
        },
    )


@app.websocket("/v1/asr/stream")
async def asr_stream_ws(websocket: WebSocket):
    """真-实时流式 ASR (WebSocket): 浏览器 AudioWorklet 直推 16k PCM, 边说边出字.

    协议:
      连接带 ?token=INTERNAL_TOKEN
      client 发 BINARY 帧 = raw 16k/16bit/mono PCM
      client 发 TEXT 帧 {"type":"end"} = 用户停止说话
      server 发 TEXT 帧 (JSON): {"type":"partial"/"final"/"error", ...}
    """
    # WebSocket 不能带自定义 header, token 走 query param.
    token = websocket.query_params.get("token", "")
    if token != settings.INTERNAL_TOKEN:
        await websocket.close(code=4403)
        logger.warning("[asr-stream ws] 鉴权失败, 拒绝连接")
        return
    await websocket.accept()

    from ocrasr.services.asr_client import (
        recognize_streaming as _recognize_streaming,
        AsrError,
        AsrConfigError,
    )

    pcm_queue: "asyncio.Queue[Any]" = asyncio.Queue()

    async def _ingest() -> None:
        """从 WS 持续读 PCM (binary) / end 信号 (text) -> 塞 pcm_queue."""
        try:
            while True:
                msg = await websocket.receive()
                msg_type = msg.get("type")
                if msg_type == "websocket.disconnect":
                    await pcm_queue.put(None)
                    return
                data_bytes = msg.get("bytes")
                if data_bytes:
                    await pcm_queue.put(data_bytes)
                    continue
                data_text = msg.get("text")
                if data_text:
                    try:
                        ctrl = json.loads(data_text)
                    except Exception:
                        continue
                    if ctrl.get("type") == "end":
                        await pcm_queue.put(None)
                        return
        except WebSocketDisconnect:
            await pcm_queue.put(None)
        except Exception as e:  # noqa: BLE001
            logger.warning(f"[asr-stream ws] ingest 异常: {e}")
            await pcm_queue.put(None)

    ingest_task = asyncio.create_task(_ingest())
    try:
        async for ev in _recognize_streaming(pcm_queue):
            await websocket.send_text(json.dumps(ev, ensure_ascii=False))
    except AsrConfigError as e:
        await websocket.send_text(json.dumps(
            {"type": "error", "error": f"ASR 服务未配置: {e!s}", "code": 503}, ensure_ascii=False))
    except AsrError as e:
        await websocket.send_text(json.dumps(
            {"type": "error", "error": f"语音识别失败: {e.message}", "code": e.code}, ensure_ascii=False))
    except WebSocketDisconnect:
        logger.info("[asr-stream ws] 客户端断开")
    except Exception as e:  # noqa: BLE001
        logger.exception("[asr-stream ws] 内部错误")
        try:
            await websocket.send_text(json.dumps(
                {"type": "error", "error": f"内部错误: {e!s}", "code": -500}, ensure_ascii=False))
        except Exception:
            pass
    finally:
        if not ingest_task.done():
            ingest_task.cancel()
        try:
            await websocket.close()
        except Exception:
            pass


# ============ 视觉 OCR ============
# 接收单页图片 (PNG/JPG; PDF 由上游先渲染为图片), 调豆包视觉模型识别. 仅识别, 不写任何库.

@app.post("/v1/ocr/image", response_model=OcrImageResponse, dependencies=[Depends(verify_internal_token)])
async def ocr_image_endpoint(file: UploadFile = File(...)):
    """单张图片 OCR: PNG/JPG -> 全文文字 (调豆包视觉多模态)."""
    from ocrasr.services.ocr_client import (
        ocr_image as _ocr_image,
        OcrError,
        OcrConfigError,
    )

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="图片为空")
    if len(image_bytes) > 8 * 1024 * 1024:
        raise HTTPException(status_code=413, detail=f"单页图片超过 8MB 上限 ({len(image_bytes) // 1024} KB)")

    try:
        result = await _ocr_image(image_bytes, mime=file.content_type or "image/png")
    except OcrConfigError as e:
        raise HTTPException(status_code=503, detail=f"OCR 服务未配置: {e!s}")
    except OcrError as e:
        raise HTTPException(status_code=500, detail=f"OCR 识别失败 [{e.code}]: {e.message}")

    return OcrImageResponse(
        text=result.get("text", ""),
        model=result.get("model", ""),
        duration_ms=result.get("duration_ms", 0),
        usage=result.get("usage", {}),
    )


@app.post("/v1/ocr/invoice", response_model=OcrInvoiceResponse, dependencies=[Depends(verify_internal_token)])
async def ocr_invoice_endpoint(file: UploadFile = File(...)):
    """单张发票结构化识别: PNG/JPG -> 票面要素 (金额/税额/购销方等). 仅识别, 是否采纳由调用方人工确认."""
    from ocrasr.services.ocr_client import (
        ocr_invoice as _ocr_invoice,
        OcrError,
        OcrConfigError,
    )

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="发票图片为空")
    if len(image_bytes) > 8 * 1024 * 1024:
        raise HTTPException(status_code=413, detail=f"发票图片超过 8MB 上限 ({len(image_bytes) // 1024} KB)")

    try:
        result = await _ocr_invoice(image_bytes, mime=file.content_type or "image/png")
    except OcrConfigError as e:
        raise HTTPException(status_code=503, detail=f"OCR 服务未配置: {e!s}")
    except OcrError as e:
        raise HTTPException(status_code=500, detail=f"发票识别失败 [{e.code}]: {e.message}")

    return OcrInvoiceResponse(
        fields=result.get("fields", {}),
        model=result.get("model", ""),
        duration_ms=result.get("duration_ms", 0),
        usage=result.get("usage", {}),
        raw=result.get("raw", ""),
    )


@app.post("/v1/ocr/document", response_model=OcrDocumentResponse, dependencies=[Depends(verify_internal_token)])
async def ocr_document_endpoint(file: UploadFile = File(...)):
    """整份材料 OCR: PDF 逐页渲染识别 / 图片直接识别 -> 全文文字.

    - PDF: 用 PyMuPDF 把每页渲染成图片, 逐页调豆包视觉模型识别后拼接全文.
      单页失败自动跳过(不阻断整份); 超过 MAX_PDF_PAGES 页只识别前若干页并标 truncated.
    - 图片(PNG/JPG): 直接识别, 等价 /v1/ocr/image 但返回统一的文档结构.
    仅识别, 不写任何库. 上游(后端)用异步线程调用, 故此处可串行慢跑.
    """
    from ocrasr.services.ocr_client import (
        ocr_image as _ocr_image,
        OcrError,
        OcrConfigError,
    )

    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="文件为空")

    ctype = (file.content_type or "").lower()
    fname = (file.filename or "").lower()
    is_pdf = ("pdf" in ctype) or fname.endswith(".pdf") or data[:5] == b"%PDF-"

    start = time.time()
    page_texts: list[str] = []
    model_used = ""
    total_pages = 0
    truncated = False

    try:
        if is_pdf:
            from ocrasr.services.pdf_processor import (
                render_pdf_to_images,
                PdfProcessError,
            )
            try:
                images, total_pages = render_pdf_to_images(data)
            except PdfProcessError as e:
                raise HTTPException(status_code=400, detail=f"PDF 处理失败: {e!s}")
            truncated = total_pages > len(images)
            for idx, img in enumerate(images, start=1):
                try:
                    r = await _ocr_image(img, mime="image/png")
                    page_texts.append((r.get("text") or "").strip())
                    model_used = model_used or r.get("model", "")
                except OcrError as e:
                    # 单页失败跳过, 占位空串, 不阻断整份识别
                    logger.warning("[ocr-doc] 第{}页识别失败, 跳过: {}", idx, e.message)
                    page_texts.append("")
        else:
            # 非 PDF 一律当单张图片处理
            if len(data) > 8 * 1024 * 1024:
                raise HTTPException(status_code=413, detail=f"图片超过 8MB 上限 ({len(data) // 1024} KB)")
            total_pages = 1
            r = await _ocr_image(data, mime=ctype or "image/png")
            page_texts.append((r.get("text") or "").strip())
            model_used = r.get("model", "")
    except HTTPException:
        raise
    except OcrConfigError as e:
        raise HTTPException(status_code=503, detail=f"OCR 服务未配置: {e!s}")
    except OcrError as e:
        raise HTTPException(status_code=500, detail=f"OCR 识别失败 [{e.code}]: {e.message}")

    # 多页拼接: 过滤空白页, 用空行分隔
    full_text = "\n\n".join(t for t in page_texts if t).strip()
    duration_ms = int((time.time() - start) * 1000)
    logger.info(
        "[ocr-doc] is_pdf={} pages={}/{} truncated={} chars={} duration={}ms",
        is_pdf, len(page_texts), total_pages, truncated, len(full_text), duration_ms,
    )
    return OcrDocumentResponse(
        text=full_text,
        pages=len(page_texts),
        total_pages=total_pages,
        truncated=truncated,
        page_texts=page_texts,
        model=model_used,
        duration_ms=duration_ms,
    )
