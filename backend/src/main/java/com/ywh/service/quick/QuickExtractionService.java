package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO;

/**
 * 规则层抽取（无大模型）：查表/正则/关键词匹配。
 * 输入转写 + 知识库（委员名单、预设议题模板），输出说话人映射、预设议题命中、
 * 临时议题候选、表决弱提示、时长统计。
 */
public interface QuickExtractionService {
    QuickExtractionVO extract(Long meetingId, AsrResult asr);
}
