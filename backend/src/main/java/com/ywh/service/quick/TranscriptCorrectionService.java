package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;

/**
 * ASR 转写后的「保结构纠错」：只改字、不动 speaker/时间戳/段数，供下游抽取与纪要使用。
 * 实现按 doubao.llm.enabled 开关切换：开 → 豆包逐段纠错；关 → 原样直通。
 */
public interface TranscriptCorrectionService {

    /** 返回纠错后的转写。失败/未启用时原样返回入参（含 null）。同一会议结果会缓存，避免重复调用大模型。 */
    AsrResult correct(Long meetingId, AsrResult asr);
}
