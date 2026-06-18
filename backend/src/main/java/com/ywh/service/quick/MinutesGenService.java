package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.QuickPolishVO;

/**
 * 大模型整理（按需）——豆包大模型接入点。
 * 把规则层结果 + 转写口语 → 议题摘要 / 决议 / 待办 / 书面纪要。
 * 仅在用户「点了润色」时调用，不是每场必调。
 */
public interface MinutesGenService {
    QuickPolishVO polish(Long meetingId, QuickExtractionVO extraction, AsrResult asr);
}
