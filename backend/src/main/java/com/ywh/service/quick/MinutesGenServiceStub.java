package com.ywh.service.quick;

import com.ywh.dto.quick.AsrResult;
import com.ywh.dto.quick.QuickExtractionVO;
import com.ywh.dto.quick.QuickPolishVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 豆包大模型整理的【桩实现】——返回演示纪要，让「润色」按钮先有结果。
 *
 * 接入你的大模型时，只改这一个文件：
 *  1. 把 extraction(规则层结果) + asr(转写) 组装成 prompt：
 *     系统提示给纪要模板/术语口径，用户内容给议题命中 + 对应转写片段；
 *  2. 调豆包对话/补全接口，要求按 QuickPolishVO 的结构输出 JSON
 *     （议题摘要 summary、决议 resolution、待办 todos、整篇 minutesMarkdown）；
 *  3. 解析返回，填进 QuickPolishVO。
 * 表决结论以人工确认为准——这里只做「口语→书面」，不替人定表决。
 */
@Service
@ConditionalOnProperty(prefix = "doubao.llm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MinutesGenServiceStub implements MinutesGenService {

    @Override
    public QuickPolishVO polish(Long meetingId, QuickExtractionVO extraction, AsrResult asr) {
        // TODO 接入豆包大模型：用 extraction + asr 组 prompt，调豆包生成结构化纪要，替换下方演示数据。
        List<QuickPolishVO.TopicSummary> topics = new ArrayList<>();

        if (extraction != null && extraction.getPresetTopicHits() != null) {
            for (QuickExtractionVO.TopicHit t : extraction.getPresetTopicHits()) {
                topics.add(QuickPolishVO.TopicSummary.builder()
                        .ref(String.valueOf(t.getTopicId()))
                        .summary("（演示）围绕「" + t.getTitle() + "」展开讨论，多数委员表示认可。")
                        .resolution("（演示）经表决通过，待人工确认。")
                        .todos(List.of("公示决议", "通知相关方"))
                        .build());
            }
        }
        if (extraction != null && extraction.getCandidateTopics() != null) {
            for (QuickExtractionVO.TopicHit t : extraction.getCandidateTopics()) {
                topics.add(QuickPolishVO.TopicSummary.builder()
                        .ref(t.getTempId())
                        .summary("（演示·临时议题）会上临时提出「" + t.getTitle() + "」，建议下次会议再议。")
                        .resolution("（演示）暂未形成决议。")
                        .todos(List.of("列入下次议程"))
                        .build());
            }
        }

        String md = "## 会议纪要（演示·待接入豆包生成）\n\n"
                + "本纪要由录音 AI 辅助生成，表决结果经人工确认。\n";

        return QuickPolishVO.builder()
                .meetingId(meetingId)
                .topics(topics)
                .minutesMarkdown(md)
                .build();
    }
}
