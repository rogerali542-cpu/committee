package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ywh.config.DoubaoProperties;
import com.ywh.dto.NewsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AI 党建新闻生成：拿会议纪要 → 豆包大模型 → 一篇党建主题新闻通稿（标题 + 正文）。
 * 与纪要生成共用方舟 Ark 凭证（doubao.llm.*）。LLM 未开启/失败时回退到党建新闻示范文案，保证「示范效果」始终可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsGenService {

    private final DoubaoProperties props;
    private final ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String SYSTEM_PROMPT = """
            你是党建融媒体中心的资深新闻编辑。请根据提供的【会议纪要】，撰写一篇面向党员群众发布的党建主题新闻通稿。
            要求：
            1. 只依据纪要内容，不得编造纪要中没有的事实、人名、数字、金额。纪要没有的信息不写。
            2. 采用规范的新闻通稿文体：
               - 导语（第一段）：点明时间、地点、召开单位和会议主题，一句话概括会议核心。
               - 主体（若干段）：分层次陈述会议研究部署的主要事项、形成的共识与决议、下一步工作安排。
               - 结尾（最后一段）：升华意义，展现党建引领基层治理、服务群众、共建共治共享的精神风貌。
            3. 突出「党建引领」视角：把会议事项与加强基层党组织建设、密切联系群众、推动社区治理等结合起来，语言正式、庄重、鼓舞人心。
            4. 标题精炼有力、体现党建高度，不超过 30 字。
            5. 正文 300—600 字，分 3—5 个自然段，段落之间用换行符分隔。
            6. 严格只输出一个 JSON 对象，不要任何解释或 Markdown 代码块包裹：
            {"title":"新闻标题","content":"新闻正文，多段用\\n分隔"}
            """;

    public NewsVO generate(String meetingTitle, String minutesText) {
        DoubaoProperties.Llm llm = props.getLlm();
        boolean llmReady = llm != null && llm.isEnabled()
                && llm.getApiKey() != null && !llm.getApiKey().isBlank();
        if (llmReady) {
            try {
                String user = "会议主题：" + safe(meetingTitle) + "\n\n【会议纪要】\n"
                        + (minutesText == null || minutesText.isBlank() ? "（暂无纪要正文，请依据会议主题合理撰写党建工作新闻）" : minutesText.trim());
                String content = callLlm(SYSTEM_PROMPT, user);
                NewsVO vo = parse(content);
                if (vo != null && vo.getContent() != null && !vo.getContent().isBlank()) {
                    vo.setSource("llm");
                    return vo;
                }
                log.warn("[NEWS] 豆包返回无法解析为新闻 JSON，使用示范文案。title={}", meetingTitle);
            } catch (Exception e) {
                log.error("[NEWS] 豆包新闻生成失败，使用示范文案。title={} msg={}", meetingTitle, e.getMessage(), e);
            }
        } else {
            log.info("[NEWS] doubao.llm 未开启，使用党建新闻示范文案。title={}", meetingTitle);
        }
        return fallback(meetingTitle);
    }

    // —— 调用方舟 chat/completions，强制 JSON —— 与纪要生成同一套 Ark 凭证
    private String callLlm(String system, String user) throws Exception {
        DoubaoProperties.Llm llm = props.getLlm();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", 0.6); // 新闻稿允许更多文采
        body.put("max_tokens", 2048);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        body.putObject("response_format").put("type", "json_object");

        String base = llm.getBaseUrl() == null ? "" : llm.getBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llm.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("Ark chat status=" + resp.statusCode() + " body=" + resp.body());
        }
        JsonNode root = mapper.readTree(resp.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private NewsVO parse(String content) throws Exception {
        if (content == null || content.isBlank()) return null;
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        JsonNode root = mapper.readTree(content.substring(start, end + 1));
        String title = root.path("title").asText("").trim();
        String body = root.path("content").asText("").trim();
        if (body.isBlank()) return null;
        if (title.isBlank()) title = "党建引领聚合力 共建共治谱新篇";
        return NewsVO.builder().title(title).content(body).build();
    }

    // —— LLM 未开启/失败时的党建新闻示范文案（保证示范效果始终可看到）——
    private NewsVO fallback(String meetingTitle) {
        String topic = safe(meetingTitle).isBlank() ? "社区党建暨基层治理工作会议" : meetingTitle;
        String content = String.join("\n",
                "近日，社区党支部召开“" + topic + "”，深入学习贯彻党的创新理论，围绕党建引领基层治理这条主线，认真研究部署下一阶段重点工作。社区党员代表、业主委员会成员参加会议。",
                "会议指出，要坚持党建引领，把党的组织优势转化为基层治理效能，推动党组织和党员在服务群众、化解矛盾、共建家园中当先锋、作表率。",
                "会议就居民群众关心的重点事项逐一研究，形成一致意见，明确了责任分工和完成时限，做到事事有回应、件件有着落。",
                "会议强调，要始终坚持以人民为中心，畅通民意表达渠道，把惠民生的实事办好、把暖民心的好事办实，不断增强居民群众的获得感、幸福感、安全感。",
                "下一步，社区党支部将持续深化党建引领基层治理，团结带领广大党员群众共建共治共享，奋力谱写和谐社区建设新篇章。");
        return NewsVO.builder()
                .title("党建引领聚合力 共建共治谱新篇")
                .content(content)
                .source("fallback")
                .build();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
