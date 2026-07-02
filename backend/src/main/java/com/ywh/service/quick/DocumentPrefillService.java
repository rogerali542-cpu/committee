package com.ywh.service.quick;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ywh.config.DoubaoProperties;
import com.ywh.dto.MeetingPrefillVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

/**
 * 新建会议 —— 上传文档/拍照件 → OCR 出文字 → 大模型抽取 {标题/日期/时间/地点/议题} → 预填表单。
 *
 * - OCR 复用 {@link DoubaoOcrService}（仅 doubao.ocr.enabled=true 时存在），用 ObjectProvider 取，没开就为 null。
 * - 抽取复用方舟 chat/completions（doubao.llm.enabled=true），与纪要生成同一套凭证。
 * - 任一环节未开启或失败，都返回 available=false 的 VO（带 message / 可能带 ocrText），绝不抛异常给前端。
 *
 * 本服务始终存在（非条件 Bean），让控制器无论 OCR/LLM 开没开都能调用并得到明确提示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPrefillService {

    private final DoubaoProperties props;
    private final ObjectMapper mapper;
    private final ObjectProvider<DoubaoOcrService> ocrProvider;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private static final String EXTRACT_SYSTEM = """
            你是业主委员会会议秘书助手。下面是从一份文件里 OCR 识别出来的文字，请完成两件事：
            一、判断文件类别 category：
            - "notice"：会议通知/开会安排——写明了会议时间、地点、议题安排，目的是通知大家来开会；
            - "material"：会议材料——供参会人传阅的资料，如报价单、施工方案、合同、财务报表、上级文件精神、工作报告等；
            - 拿不准时：有明确的开会时间+地点安排就算 notice，否则算 material。
            二、若是 notice，从原文提取“新建会议”所需信息，仅依据原文、不得编造；material 各字段留空即可。
            只输出一个 JSON 对象，不要任何解释、不要 Markdown 代码块：
            {"category":"notice或material","title":"会议名称","meetingDate":"YYYY-MM-DD","meetingTime":"HH:mm","location":"会议地点","topics":["议题标题1","议题标题2"]}
            要求：
            1. 只从给定文字提取；缺失的字段用空字符串 ""，topics 缺失用空数组 []，不要编造。
            2. 日期归一化为 YYYY-MM-DD；若原文只有“X月X日”没有年份，用我给你的“当前年份”。
            3. 时间归一化为 24 小时制 HH:mm（如“下午两点半”→“14:30”）。
            4. topics 只取会议要讨论/审议/表决的议题标题，简洁，一条一个，不要把整段说明塞进去。
            """;

    /** 识别并抽取。任何不可用/失败都返回 available=false 的 VO（带 message），不抛异常。 */
    public MeetingPrefillVO parse(String filename, String fileType, byte[] data) {
        if (data == null || data.length == 0) {
            return MeetingPrefillVO.unavailable("文件为空");
        }
        DoubaoOcrService ocr = props.getOcr().isEnabled() ? ocrProvider.getIfAvailable() : null;
        if (ocr == null) {
            return MeetingPrefillVO.unavailable("文档识别未开启：需配置 doubao.ocr.enabled=true 并启动 ocr-asr-service");
        }
        String text;
        try {
            text = ocr.ocrDocumentSync(filename, fileType, data);
        } catch (Exception e) {
            log.warn("[PREFILL] OCR 失败 file={}: {}", filename, e.getMessage(), e);
            return MeetingPrefillVO.unavailable("文档识别服务暂时不可用，请稍后再试或手动填写");
        }
        if (text == null || text.isBlank()) {
            return MeetingPrefillVO.unavailable("未能从文档中识别出文字，请换更清晰的图片或手动填写");
        }
        if (!props.getLlm().isEnabled()) {
            return MeetingPrefillVO.textOnly(text, "已识别文字，但 AI 抽取未开启（doubao.llm.enabled=true），请手动整理");
        }
        try {
            MeetingPrefillVO vo = llmExtract(text);
            vo.setAvailable(true);
            vo.setOcrText(text);
            if (vo.getMessage() == null || vo.getMessage().isBlank()) {
                vo.setMessage("material".equals(vo.getCategory()) ? "已识别为会议材料" : "已识别并预填，请核对");
            }
            return vo;
        } catch (Exception e) {
            log.warn("[PREFILL] 大模型抽取失败: {}", e.getMessage(), e);
            return MeetingPrefillVO.textOnly(text, "已识别文字，但智能填写失败，请手动整理");
        }
    }

    // —— 调用方舟 chat/completions，让模型把 OCR 文字抽成结构化字段 ——
    private MeetingPrefillVO llmExtract(String ocrText) throws Exception {
        DoubaoProperties.Llm llm = props.getLlm();
        String user = "当前年份：" + LocalDate.now().getYear() + "\n\n会议资料识别文字如下：\n" + ocrText;

        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", 1024);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", EXTRACT_SYSTEM);
        messages.addObject().put("role", "user").put("content", user);
        body.putObject("response_format").put("type", "json_object");

        String url = trimTrailingSlash(llm.getBaseUrl()) + "/chat/completions";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llm.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("Ark chat status=" + resp.statusCode());
        }
        JsonNode root = mapper.readTree(resp.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        MeetingPrefillVO vo = parseExtraction(content);
        vo.setTokens(root.path("usage").path("total_tokens").asLong(0));
        return vo;
    }

    // —— 解析模型输出的 JSON（容错：截取首个 { 到末个 }）——
    private MeetingPrefillVO parseExtraction(String content) throws Exception {
        MeetingPrefillVO vo = new MeetingPrefillVO();
        if (content == null || content.isBlank()) return vo;
        int s = content.indexOf('{'), e = content.lastIndexOf('}');
        if (s < 0 || e <= s) return vo;
        JsonNode n = mapper.readTree(content.substring(s, e + 1));
        String cat = n.path("category").asText("").trim().toLowerCase();
        vo.setCategory("notice".equals(cat) || "material".equals(cat) ? cat : "");
        vo.setTitle(n.path("title").asText("").trim());
        vo.setMeetingDate(n.path("meetingDate").asText("").trim());
        vo.setMeetingTime(n.path("meetingTime").asText("").trim());
        vo.setLocation(n.path("location").asText("").trim());
        JsonNode topics = n.path("topics");
        if (topics.isArray()) {
            for (JsonNode t : topics) {
                String title = t.isObject() ? t.path("title").asText("") : t.asText("");
                if (title != null && !title.isBlank()) vo.getTopics().add(title.trim());
            }
        }
        return vo;
    }

    private String trimTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
