import json
import time
from typing import Any, AsyncGenerator, Dict

from loguru import logger
from openai import AsyncOpenAI
from pydantic import ValidationError

from ocrasr.config import settings
from ocrasr.schemas import AIResultSchema


def _preview_text(raw: Any, limit: int = 300) -> str:
    text = raw if isinstance(raw, str) else str(raw)
    compact = text.replace("\n", "\\n").replace("\r", "")
    return compact[:limit] + ("..." if len(compact) > limit else "")


def _normalize_result_data(data: dict) -> dict:
    """将 LLM 可能返回的字段归一化，避免 schema 校验失败。"""
    for field in ("key_issues", "code_suggestions"):
        items = data.get(field)
        if not isinstance(items, list):
            continue
        normalized = []
        for item in items:
            if isinstance(item, dict):
                normalized.append(item)
            elif isinstance(item, str):
                normalized.append({"description": item})
        data[field] = normalized
    # LLM 有时把 summary 返回为 dict，强制转为 JSON 字符串
    if "summary" in data and isinstance(data["summary"], dict):
        data["summary"] = json.dumps(data["summary"], ensure_ascii=False)
    return data


class LLMClient:
    def __init__(self):
        self.client = AsyncOpenAI(
            api_key=settings.ARK_API_KEY,
            base_url=settings.ARK_BASE_URL,
        )

    async def chat_completion(
        self,
        system_prompt: str,
        user_prompt: str,
        response_model: bool = False,
        retries: int = 2,
    ) -> Dict[str, Any]:
        """Call LLM with JSON parsing, schema validation and retries."""
        start_time = time.time()

        for attempt in range(retries + 1):
            content = ""
            try:
                response = await self.client.chat.completions.create(
                    model=settings.ARK_MODEL_ID,
                    messages=[
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                    temperature=0.3,
                    timeout=120.0,
                    extra_body={
                        "reasoning_effort": settings.LLM_REASONING_EFFORT,
                    },
                )

                content = response.choices[0].message.content or ""
                duration_ms = int((time.time() - start_time) * 1000)

                result_data = json.loads(content)
                if response_model:
                    result_data = _normalize_result_data(result_data)
                    AIResultSchema(**result_data)

                return {
                    "result": result_data,
                    "model": response.model,
                    "usage": {
                        "prompt_tokens": response.usage.prompt_tokens,
                        "completion_tokens": response.usage.completion_tokens,
                    },
                    "duration_ms": duration_ms,
                    "raw_output": content,
                }
            except json.JSONDecodeError as exc:
                preview = _preview_text(content)
                message = (
                    f"LLM returned non-JSON content (attempt {attempt + 1}/{retries + 1}). "
                    f"preview={preview}"
                )
                logger.warning(f"{message}; error={str(exc)}")
                if attempt == retries:
                    raise ValueError(message) from exc
            except ValidationError as exc:
                preview = _preview_text(content)
                message = (
                    f"LLM returned JSON but failed schema validation (attempt {attempt + 1}/{retries + 1}). "
                    f"preview={preview}"
                )
                logger.warning(f"{message}; error={str(exc)}")
                if attempt == retries:
                    raise ValueError(message) from exc
            except Exception as exc:
                logger.warning(f"LLM attempt {attempt + 1} failed: {str(exc)}")
                if attempt == retries:
                    raise

        raise RuntimeError("LLM completion exhausted retries without a result.")

    async def chat_completion_text_stream(
        self,
        system_prompt: str,
        user_prompt: str,
    ) -> AsyncGenerator[Dict[str, Any], None]:
        """以流式方式返回纯文本内容，供 SSE 场景使用。"""
        stream = await self.client.chat.completions.create(
            model=settings.ARK_MODEL_ID,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.3,
            timeout=120.0,
            stream=True,
            stream_options={"include_usage": True},
            extra_body={
                "reasoning_effort": settings.LLM_REASONING_EFFORT,
            },
        )

        async for chunk in stream:
            choice = chunk.choices[0] if chunk.choices else None
            delta = ""
            if choice and getattr(choice, "delta", None):
                delta = choice.delta.content or ""

            usage = getattr(chunk, "usage", None)
            yield {
                "delta": delta,
                "usage": {
                    "prompt_tokens": getattr(usage, "prompt_tokens", None) if usage else None,
                    "completion_tokens": getattr(usage, "completion_tokens", None) if usage else None,
                },
                "finish_reason": getattr(choice, "finish_reason", None) if choice else None,
            }


llm_client = LLMClient()

