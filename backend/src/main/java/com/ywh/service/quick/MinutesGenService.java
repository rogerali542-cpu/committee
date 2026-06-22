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

    QuickPolishVO polish(Long meetingId, String meetingContext, QuickExtractionVO extraction, AsrResult asr);

    /**
     * 单议题摘要（按需）：把某个议题命中的口语片段整理成一段书面纪要正文。
     * 用于确认步给通报/讨论类议题生成正文（非每场必调）。
     *
     * @param title        议题标题
     * @param type         议题类型（notice/discussion/decision...）
     * @param segmentTexts 该议题命中片段的转写文本
     * @return 书面正文；无内容或失败返回空串
     */
    String summarizeTopic(String title, String type, java.util.List<String> segmentTexts);
}
