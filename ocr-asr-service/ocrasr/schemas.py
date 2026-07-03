from __future__ import annotations

from typing import Dict, List, Optional

from pydantic import BaseModel, ConfigDict, Field


# ============ 语音识别 (ASR) ============

class AsrRecognizeResponse(BaseModel):
    """语音识别结果"""
    text: str = ""
    utterances: List[Dict] = Field(default_factory=list)
    duration_ms: int = 0
    request_id: str = ""
    log_id: str = ""


# ============ 会议纪要 / 行动建议 (基于转写片段) ============

class MeetingSegment(BaseModel):
    """会议转写片段"""
    id: Optional[int] = None
    speaker_label: str = ""
    speaker_user_id: Optional[int] = None
    start_ms: int = 0
    end_ms: int = 0
    text: str = ""


class MeetingActionExtractRequest(BaseModel):
    """提取行动建议入参"""
    meeting: Dict = Field(default_factory=dict)
    segments: List[MeetingSegment] = Field(default_factory=list)
    schema_version: str = Field(default="1.0")


class MeetingActionExtractResponse(BaseModel):
    """行动建议提取结果"""
    actions: List[Dict] = Field(default_factory=list)
    tokens: Optional[Dict] = None


class MeetingMinutesRequest(BaseModel):
    """生成会议纪要入参"""
    segments: List[MeetingSegment] = Field(default_factory=list)
    schema_version: str = Field(default="1.0")


class MeetingMinutesResponse(BaseModel):
    """结构化会议纪要"""
    summary: str = ""
    decisions: List[str] = Field(default_factory=list)
    risks: List[str] = Field(default_factory=list)
    open_questions: List[str] = Field(default_factory=list)
    tokens: Optional[Dict] = None


# ============ 议题意见 AI 助手 ============

class OpinionAssistRequest(BaseModel):
    """委员意见 AI 助手入参. mode=polish 把已有意见润色得正式规范; mode=draft 根据口头描述代拟发言."""
    mode: str = "polish"
    topic_title: str = ""
    topic_type: str = ""       # notice/discussion/decision
    speaker_role: str = ""     # 主任/副主任/委员
    text: str


class OpinionAssistResponse(BaseModel):
    """AI 助手产出的意见文本 (调用方展示给委员确认/修改, 不直接入库)"""
    text: str = ""
    tokens: Optional[Dict] = None


# ============ 视觉 OCR ============

class OcrImageResponse(BaseModel):
    """单页扫描件 OCR 全文识别结果 (豆包视觉模型)"""
    text: str = ""
    model: str = ""
    duration_ms: int = 0
    usage: Dict = Field(default_factory=dict)


class OcrInvoiceResponse(BaseModel):
    """增值税发票结构化识别结果 (豆包视觉模型). fields 含票面要素, 是否采纳由调用方人工确认."""
    fields: Dict = Field(default_factory=dict)
    model: str = ""
    duration_ms: int = 0
    usage: Dict = Field(default_factory=dict)
    raw: str = ""


class OcrDocumentResponse(BaseModel):
    """整份材料 OCR 全文识别结果 (PDF 逐页渲染识别 / 单图直接识别).

    text 为多页拼接后的全文; page_texts 为每页文字 (便于上游按页处理).
    truncated=True 表示 PDF 页数超过上限被截断, 只识别了前若干页.
    """
    text: str = ""
    pages: int = 0                                       # 实际识别页数
    total_pages: int = 0                                 # 文档总页数 (PDF)
    truncated: bool = False
    page_texts: List[str] = Field(default_factory=list)
    model: str = ""
    duration_ms: int = 0


# ============ LLM 通用结果 schema (llm_client 校验用; 纪要走 response_model=False 不强校验) ============

class AIResultSchema(BaseModel):
    """LLM 返回结果的宽松 schema, 允许额外字段."""
    model_config = ConfigDict(extra="allow")

    completeness: Optional[float] = None
    quality: Optional[float] = None
    timeliness: Optional[float] = None
    summary: Optional[str] = None
    comment: Optional[str] = None
    type_of_changes: Optional[List[str]] = None
    key_issues: Optional[List[dict]] = None
    code_suggestions: Optional[List[dict]] = None
    strengths: Optional[List[str]] = None
    improvements: Optional[List[str]] = None
    risks: Optional[List[str]] = None
    criteria_scores: Optional[Dict[str, Dict[str, float]]] = None
