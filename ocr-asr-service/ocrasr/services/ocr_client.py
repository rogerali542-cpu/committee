"""
AI 新建项目 - 视觉 OCR 客户端 (扫描件 PDF 识图)
复用现有 ARK_API_KEY, 走豆包视觉多模态模型把页面图渲染识别为纯文字.
对应文档: .0features/20260526-ai-create-project/02_design.md (OCR 章节)
"""
from __future__ import annotations

import base64
import json
import time
from typing import Any, Dict, Optional

from loguru import logger
from openai import AsyncOpenAI

from ocrasr.config import settings


class OcrError(Exception):
    """OCR 调用失败 (网络 / 视觉模型未开通 / 返回异常)"""
    def __init__(self, message: str, code: int = -1):
        super().__init__(message)
        self.message = message
        self.code = code


class OcrConfigError(Exception):
    """OCR 配置缺失 (ARK_API_KEY 等)"""


_OCR_SYSTEM_PROMPT = (
    "你是 OCR 文字识别专家. 请识别图片中的所有文字, "
    "严格按原文顺序和换行输出, 不要添加任何解释, 标注或 Markdown 包裹. "
    "表格用 ' | ' 分隔单元格, 每行一条. "
    "如果图片无文字或不可识别, 返回空串."
)

_OCR_USER_PROMPT = "请输出图片中的全部文字 (按原文顺序与换行):"


def _client() -> AsyncOpenAI:
    if not settings.ARK_API_KEY or not settings.ARK_BASE_URL:
        raise OcrConfigError("缺少 ARK_API_KEY 或 ARK_BASE_URL, 无法调用视觉模型")
    return AsyncOpenAI(api_key=settings.ARK_API_KEY, base_url=settings.ARK_BASE_URL)


async def ocr_image(png_bytes: bytes, mime: str = "image/png") -> Dict[str, Any]:
    """
    把单张图 (PNG / JPG bytes) 喂给豆包视觉模型, 返回识别文字.
    输出: {"text": str, "model": str, "duration_ms": int, "usage": {...}}

    注意:
    - 模型 ID 走 settings.OCR_VISION_MODEL_ID, 默认 doubao-1-5-vision-pro-32k-250115
    - 失败统一抛 OcrError, 上游接住后 fallback 到「跳过该页」即可, 不阻断整篇解析
    """
    if not png_bytes:
        raise OcrError("OCR 输入图片为空", code=400)

    start = time.time()
    b64 = base64.b64encode(png_bytes).decode("ascii")
    mime_type = mime or "image/png"
    data_url = f"data:{mime_type};base64,{b64}"

    model_id = getattr(settings, "OCR_VISION_MODEL_ID", "") or "doubao-1-5-vision-pro-32k-250115"

    try:
        client = _client()
        # 火山方舟 OpenAI 兼容接口, content 用 list 形式塞 image_url
        response = await client.chat.completions.create(
            model=model_id,
            messages=[
                {"role": "system", "content": _OCR_SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": _OCR_USER_PROMPT},
                        {"type": "image_url", "image_url": {"url": data_url}},
                    ],
                },
            ],
            temperature=0.0,  # OCR 不允许发挥, 直接抄
            timeout=90.0,
        )
    except OcrConfigError:
        raise
    except Exception as e:  # noqa: BLE001
        # 包括 RateLimitError / APIConnectionError / 模型未开通 等
        raise OcrError(f"视觉模型调用失败: {e!s}", code=-1) from e

    text = ""
    try:
        text = (response.choices[0].message.content or "").strip()
    except (IndexError, AttributeError) as e:
        raise OcrError(f"视觉模型返回结构异常: {e!s}", code=-2) from e

    duration_ms = int((time.time() - start) * 1000)
    usage = {
        "prompt_tokens": getattr(response.usage, "prompt_tokens", None) if response.usage else None,
        "completion_tokens": getattr(response.usage, "completion_tokens", None) if response.usage else None,
    }
    logger.info(
        "[OCR vision] model={} duration={}ms text_chars={} tokens_in={} tokens_out={}",
        model_id, duration_ms, len(text),
        usage.get("prompt_tokens"), usage.get("completion_tokens"),
    )
    return {
        "text": text,
        "model": model_id,
        "duration_ms": duration_ms,
        "usage": usage,
    }


# ============ 发票结构化识别 (增值税发票要素) ============
# 与 ocr_image 不同: ocr_image 出"全文文字", 这里出"票面结构化字段"(金额/税额/购销方等).
# 用途: 加速财务回款/收入确认录入, 识别结果以"待核对"草稿态预填表单, 人工确认才落库(防御在调用方做).
# 关键约束: 金额按票面原样, 不确定填 null, 严禁编造数字(写财务库容错极低).

_INVOICE_PROMPT = """你是发票要素识别助手. 请阅读这张中国增值税发票图片, 只输出一个 JSON 对象, 不要任何额外文字、解释或 markdown 代码块标记. 字段如下:
- invoice_type: 发票类型, 取值 增值税专用发票 / 增值税普通发票 / 电子发票 / 其他 / 非发票
- invoice_code: 发票代码, 字符串, 新版全电发票若无代码填 null
- invoice_no: 发票号码, 字符串, 无法识别填 null
- invoice_date: 开票日期, 格式 YYYY-MM-DD, 无法识别填 null
- buyer_name: 购买方名称, 无法识别填 null
- buyer_tax_id: 购买方纳税人识别号(统一社会信用代码), 无法识别填 null
- seller_name: 销售方名称, 无法识别填 null
- seller_tax_id: 销售方纳税人识别号, 无法识别填 null
- item_name: 货物或应税劳务、服务名称; 多行时取主要项或汇总简述(如 技术服务费), 无法识别填 null
- amount_excl_tax: 不含税金额合计, 纯数字(不带货币符号和千分位逗号), 无法识别填 null
- tax_rate: 税率, 字符串如 13% 或 6%; 多种税率时填 多税率, 无法识别填 null
- tax_amount: 税额合计, 纯数字, 无法识别填 null
- total_amount: 价税合计(含税总额, 小写数字), 纯数字, 这是最关键字段, 无法识别填 null
- total_amount_cn: 价税合计大写, 字符串, 无法识别填 null
- drawer: 开票人, 无法识别填 null
- remark: 发票备注栏内容, 无内容填 null
- note: 若图片模糊、被遮挡或非发票, 简述原因, 否则填 null

要求: 金额必须按票面印刷数字原样输出, 不要四舍五入, 不要自行推算, 不确定就填 null. 严禁编造任何数字或名称."""

# 识别字段白名单: 只透传这些键, 防止模型多吐字段污染下游
_INVOICE_FIELD_KEYS = (
    "invoice_type", "invoice_code", "invoice_no", "invoice_date",
    "buyer_name", "buyer_tax_id", "seller_name", "seller_tax_id", "item_name",
    "amount_excl_tax", "tax_rate", "tax_amount", "total_amount", "total_amount_cn",
    "drawer", "remark", "note",
)


def _parse_invoice_json(text: str) -> Optional[Dict[str, Any]]:
    """从模型返回里宽松解析 JSON 对象, 容忍 markdown 代码块包裹与前后缀."""
    if not text:
        return None
    s = text.strip()
    if s.startswith("```"):
        s = s.strip("`")
        if s[:4].lower() == "json":
            s = s[4:]
        s = s.strip()
    try:
        return json.loads(s)
    except json.JSONDecodeError:
        left = s.find("{")
        right = s.rfind("}")
        if left != -1 and right != -1 and right > left:
            try:
                return json.loads(s[left:right + 1])
            except json.JSONDecodeError:
                return None
        return None


async def ocr_invoice(image_bytes: bytes, mime: str = "image/png") -> Dict[str, Any]:
    """
    把单张发票图 (PNG/JPG bytes) 喂给豆包视觉模型, 返回结构化票面要素.
    输出: {"fields": {17个识别字段}, "model": str, "duration_ms": int, "usage": {...}, "raw": str}

    注意:
    - 模型 ID 走 settings.OCR_VISION_MODEL_ID, 与 ocr_image 共用 ARK 配置.
    - 返回结构化字段, 但识别结果是否采纳由调用方人工确认; 本函数不写任何库.
    - 模型对非发票/模糊图会返回 invoice_type=非发票 且字段为 null, 这是正常结果, 不抛异常.
    - 仅当返回无法解析为 JSON 时抛 OcrError, 调用方兜底为"识别失败, 手填".
    """
    if not image_bytes:
        raise OcrError("发票识别输入图片为空", code=400)

    start = time.time()
    b64 = base64.b64encode(image_bytes).decode("ascii")
    mime_type = mime or "image/png"
    data_url = f"data:{mime_type};base64,{b64}"
    model_id = getattr(settings, "OCR_VISION_MODEL_ID", "") or "doubao-1-5-vision-pro-32k-250115"

    messages = [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": _INVOICE_PROMPT},
                {"type": "image_url", "image_url": {"url": data_url}},
            ],
        }
    ]

    try:
        client = _client()
        try:
            # 优先用 json_object 强约束输出; 部分模型不支持则回退普通调用
            response = await client.chat.completions.create(
                model=model_id,
                messages=messages,
                temperature=0.0,  # 识别任务要确定性, 关掉随机
                timeout=90.0,
                response_format={"type": "json_object"},
            )
        except OcrConfigError:
            raise
        except Exception:
            response = await client.chat.completions.create(
                model=model_id,
                messages=messages,
                temperature=0.0,
                timeout=90.0,
            )
    except OcrConfigError:
        raise
    except Exception as e:  # noqa: BLE001 网络/限流/模型未开通等
        raise OcrError(f"发票识别视觉模型调用失败: {e!s}", code=-1) from e

    try:
        raw = (response.choices[0].message.content or "").strip()
    except (IndexError, AttributeError) as e:
        raise OcrError(f"发票识别返回结构异常: {e!s}", code=-2) from e

    parsed = _parse_invoice_json(raw)
    if parsed is None or not isinstance(parsed, dict):
        raise OcrError("发票识别返回无法解析为 JSON", code=-3)

    # 只保留白名单字段, 缺失键补 None
    fields = {key: parsed.get(key) for key in _INVOICE_FIELD_KEYS}

    duration_ms = int((time.time() - start) * 1000)
    usage = {
        "prompt_tokens": getattr(response.usage, "prompt_tokens", None) if response.usage else None,
        "completion_tokens": getattr(response.usage, "completion_tokens", None) if response.usage else None,
    }
    logger.info(
        "[OCR invoice] model={} duration={}ms type={} total_amount={} tokens_in={} tokens_out={}",
        model_id, duration_ms, fields.get("invoice_type"), fields.get("total_amount"),
        usage.get("prompt_tokens"), usage.get("completion_tokens"),
    )
    return {
        "fields": fields,
        "model": model_id,
        "duration_ms": duration_ms,
        "usage": usage,
        "raw": raw,
    }
