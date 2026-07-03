from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Iterable


def _load_env_file(path: str) -> None:
    if not os.path.exists(path):
        return
    # 仅在系统环境变量不存在时才回填 .env, 这样线上可用环境变量覆盖配置.
    with open(path, "r", encoding="utf-8") as f:
        for raw in f.readlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip().strip("'").strip('"')
            if key and key not in os.environ:
                os.environ[key] = value


def _require(value: str, name: str) -> str:
    if not value:
        raise ValueError(f"Missing required config: {name}")
    return value


def _to_int(value: str, name: str, default: int) -> int:
    if not value:
        return default
    try:
        return int(value)
    except ValueError as exc:
        raise ValueError(f"Invalid int for {name}: {value}") from exc


def _to_bool(value: str) -> bool:
    return value.strip().lower() in ("1", "true", "yes")


@dataclass(frozen=True)
class Settings:
    # 内部接口鉴权
    INTERNAL_TOKEN: str
    PORT: int
    LOG_LEVEL: str
    # 火山方舟 ARK (豆包) - OCR 视觉 + 会议纪要大模型共用
    ARK_API_KEY: str
    ARK_MODEL_ID: str
    ARK_BASE_URL: str
    LLM_REASONING_EFFORT: str
    # 视觉 OCR 模型 ID
    OCR_VISION_MODEL_ID: str
    # 火山引擎大模型流式语音识别 (ASR)
    VOLC_ASR_ENABLED: bool
    VOLC_ASR_ENDPOINT: str
    VOLC_ASR_APP_ID: str
    VOLC_ASR_ACCESS_TOKEN: str
    VOLC_ASR_SECRET_KEY: str
    VOLC_ASR_RESOURCE_ID: str
    VOLC_ASR_MODEL_NAME: str
    VOLC_ASR_SAMPLE_RATE: int
    VOLC_ASR_BITS: int
    VOLC_ASR_CHANNEL: int
    VOLC_ASR_FORMAT: str
    VOLC_ASR_CODEC: str
    VOLC_ASR_ENABLE_PUNC: bool
    VOLC_ASR_MAX_FILE_SIZE_BYTES: int
    VOLC_ASR_CHUNK_SIZE_BYTES: int
    VOLC_ASR_CONNECT_TIMEOUT_MS: int
    VOLC_ASR_READ_TIMEOUT_MS: int

    def validate(self) -> None:
        # OCR 与 LLM 纪要均依赖 ARK; INTERNAL_TOKEN 用于接口鉴权, 三者强制.
        _require(self.ARK_API_KEY, "ARK_API_KEY")
        _require(self.ARK_BASE_URL, "ARK_BASE_URL")
        _require(self.INTERNAL_TOKEN, "INTERNAL_TOKEN")
        # ASR 字段非强制: 缺失则 VOLC_ASR_ENABLED 应为 false, 仅 OCR 可用.
        # 会议纪要依赖 ARK_MODEL_ID, 缺失时仅纪要端点不可用, 不阻断启动.

    @property
    def safe_summary(self) -> Iterable[str]:
        # 启动日志只输出非敏感配置摘要, 避免泄漏密钥.
        return [
            f"ARK_MODEL_ID={self.ARK_MODEL_ID}",
            f"OCR_VISION_MODEL_ID={self.OCR_VISION_MODEL_ID}",
            f"VOLC_ASR_ENABLED={self.VOLC_ASR_ENABLED}",
            f"PORT={self.PORT}",
            f"LOG_LEVEL={self.LOG_LEVEL}",
        ]


_env_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), ".env")
_load_env_file(_env_path)

settings = Settings(
    INTERNAL_TOKEN=os.getenv("INTERNAL_TOKEN", ""),
    PORT=_to_int(os.getenv("PORT", ""), "PORT", 8003),
    LOG_LEVEL=os.getenv("LOG_LEVEL", "INFO"),
    ARK_API_KEY=os.getenv("ARK_API_KEY", ""),
    ARK_MODEL_ID=os.getenv("ARK_MODEL_ID", ""),
    ARK_BASE_URL=os.getenv("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3"),
    LLM_REASONING_EFFORT=os.getenv("LLM_REASONING_EFFORT", "minimal"),
    OCR_VISION_MODEL_ID=os.getenv("OCR_VISION_MODEL_ID", "doubao-1-5-vision-pro-32k-250115"),
    VOLC_ASR_ENABLED=_to_bool(os.getenv("VOLC_ASR_ENABLED", "")),
    VOLC_ASR_ENDPOINT=os.getenv("VOLC_ASR_ENDPOINT", "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel"),
    VOLC_ASR_APP_ID=os.getenv("VOLC_ASR_APP_ID", ""),
    VOLC_ASR_ACCESS_TOKEN=os.getenv("VOLC_ASR_ACCESS_TOKEN", ""),
    VOLC_ASR_SECRET_KEY=os.getenv("VOLC_ASR_SECRET_KEY", ""),
    VOLC_ASR_RESOURCE_ID=os.getenv("VOLC_ASR_RESOURCE_ID", "volc.bigasr.sauc.duration"),
    VOLC_ASR_MODEL_NAME=os.getenv("VOLC_ASR_MODEL_NAME", "bigmodel"),
    VOLC_ASR_SAMPLE_RATE=_to_int(os.getenv("VOLC_ASR_SAMPLE_RATE", ""), "VOLC_ASR_SAMPLE_RATE", 16000),
    VOLC_ASR_BITS=_to_int(os.getenv("VOLC_ASR_BITS", ""), "VOLC_ASR_BITS", 16),
    VOLC_ASR_CHANNEL=_to_int(os.getenv("VOLC_ASR_CHANNEL", ""), "VOLC_ASR_CHANNEL", 1),
    VOLC_ASR_FORMAT=os.getenv("VOLC_ASR_FORMAT", "pcm"),
    VOLC_ASR_CODEC=os.getenv("VOLC_ASR_CODEC", "raw"),
    VOLC_ASR_ENABLE_PUNC=os.getenv("VOLC_ASR_ENABLE_PUNC", "true").strip().lower() in ("1", "true", "yes"),
    VOLC_ASR_MAX_FILE_SIZE_BYTES=_to_int(os.getenv("VOLC_ASR_MAX_FILE_SIZE_BYTES", ""), "VOLC_ASR_MAX_FILE_SIZE_BYTES", 6291456),
    VOLC_ASR_CHUNK_SIZE_BYTES=_to_int(os.getenv("VOLC_ASR_CHUNK_SIZE_BYTES", ""), "VOLC_ASR_CHUNK_SIZE_BYTES", 3200),
    VOLC_ASR_CONNECT_TIMEOUT_MS=_to_int(os.getenv("VOLC_ASR_CONNECT_TIMEOUT_MS", ""), "VOLC_ASR_CONNECT_TIMEOUT_MS", 10000),
    VOLC_ASR_READ_TIMEOUT_MS=_to_int(os.getenv("VOLC_ASR_READ_TIMEOUT_MS", ""), "VOLC_ASR_READ_TIMEOUT_MS", 60000),
)

settings.validate()
