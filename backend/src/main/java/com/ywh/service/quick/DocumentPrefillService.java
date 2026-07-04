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
import java.util.List;

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

    private static final String EXTRACT_MULTI_SYSTEM = """
            你是业主委员会会议秘书助手。用户一次上传了多个文件，下面每个文件用【文件N：文件名】标注，内容是各自 OCR 出来的文字。请完成两件事：
            一、逐个判断每个文件的类别，按【文件N】的先后顺序输出 fileCategories 数组，数组长度必须等于文件数：
            - "notice"：会议通知/开会安排——写明会议时间、地点、议题，目的是通知大家来开会；同一份通知被拆成多页/多张照片时，每一份都算 notice。
            - "material"：会议材料——供参会人传阅的资料，如报价单、施工方案、合同、财务报表、上级文件精神、工作报告等。
            - 拿不准时：有明确的开会时间+地点安排就算 notice，否则算 material。
            二、把所有 notice 文件的内容合并（多页通知合并成一份），提取“新建会议”所需信息，仅依据原文、不得编造；material 文件不参与提取。
            三、若有两份及以上 notice 文件，且它们给出的会议时间或会议地点互相矛盾，用一句话写进 conflict 字段（如“两份通知的会议时间不一致”或“通知的会议地点不一致”）；不矛盾、或只有一份通知，则 conflict 为空字符串 ""。
            只输出一个 JSON 对象，不要任何解释、不要 Markdown 代码块：
            {"fileCategories":["notice或material", ...],"title":"会议名称","meetingDate":"YYYY-MM-DD","meetingTime":"HH:mm","location":"会议地点","topics":["议题标题1","议题标题2"],"conflict":"冲突说明或空串"}
            要求：
            1. 只从给定文字提取；缺失的字段用空字符串 ""，topics 缺失用空数组 []，不要编造。
            2. 日期归一化为 YYYY-MM-DD；只有“X月X日”没有年份时用我给的“当前年份”。
            3. 时间归一化为 24 小时制 HH:mm（如“下午两点半”→“14:30”）。
            4. topics 只取会议要讨论/审议/表决的议题标题，简洁，一条一个。
            5. 若没有任何 notice 文件，title/meetingDate/meetingTime/location 留空、topics 留空数组，fileCategories 照常逐个给出，conflict 为空串。
            """;

    /** 多文件识别的输入项：文件名 / 扩展名 / 大小 / 原始字节。 */
    public record Doc(String filename, String fileType, long fileSize, byte[] data) {}

    /**
     * 多文件统一识别：逐个 OCR → 合并成带文件标号的文本 → 一次大模型调用，逐文件判类 + 合并抽取通知信息。
     * 无论成败都会把每个文件填进 vo.files（category 可能为空），便于前端把文件作为材料挂载。
     */
    public MeetingPrefillVO parseMulti(List<Doc> docs) {
        MeetingPrefillVO vo = new MeetingPrefillVO();
        if (docs == null || docs.isEmpty()) return MeetingPrefillVO.unavailable("文件为空");
        for (Doc d : docs) {
            MeetingPrefillVO.FileInfo fi = new MeetingPrefillVO.FileInfo();
            fi.setFileName(d.filename() == null ? "" : d.filename());
            fi.setFileType(d.fileType() == null ? "" : d.fileType());
            fi.setFileSize(d.fileSize());
            vo.getFiles().add(fi);
        }
        DoubaoOcrService ocr = props.getOcr().isEnabled() ? ocrProvider.getIfAvailable() : null;
        if (ocr == null) {
            vo.setAvailable(false);
            vo.setMessage("文档识别未开启：需配置 doubao.ocr.enabled=true 并启动 ocr-asr-service");
            return vo;
        }
        StringBuilder combined = new StringBuilder();
        int okCount = 0;
        for (int i = 0; i < docs.size(); i++) {
            Doc d = docs.get(i);
            String t = "";
            try {
                t = ocr.ocrDocumentSync(d.filename(), d.fileType(), d.data());
            } catch (Exception e) {
                log.warn("[PREFILL-MULTI] OCR 失败 file={}: {}", d.filename(), e.getMessage());
            }
            combined.append("【文件").append(i + 1).append("：").append(d.filename() == null ? "" : d.filename()).append("】\n");
            if (t != null && !t.isBlank()) {
                okCount++;
                combined.append(t.trim());
            } else {
                combined.append("（未能识别出文字）");
            }
            combined.append("\n\n");
        }
        if (okCount == 0) {
            vo.setAvailable(false);
            vo.setMessage("未能从文件中识别出文字，请换更清晰的图片或手动填写");
            return vo;
        }
        vo.setOcrText(combined.toString());
        if (!props.getLlm().isEnabled()) {
            vo.setAvailable(false);
            vo.setMessage("已识别文字，但 AI 抽取未开启（doubao.llm.enabled=true），请手动整理");
            return vo;
        }
        try {
            llmExtractMulti(combined.toString(), docs.size(), vo);
            vo.setAvailable(true);
            boolean anyNotice = vo.getFiles().stream().anyMatch(f -> "notice".equals(f.getCategory()));
            vo.setCategory(anyNotice ? "notice" : "material");
            if (vo.getMessage() == null || vo.getMessage().isBlank()) {
                vo.setMessage(anyNotice ? "已识别并预填，请核对" : "已识别为会议材料");
            }
            return vo;
        } catch (Exception e) {
            log.warn("[PREFILL-MULTI] 大模型抽取失败: {}", e.getMessage(), e);
            vo.setAvailable(false);
            vo.setMessage("已识别文字，但智能填写失败，请手动整理");
            return vo;
        }
    }

    // —— 多文件：一次调用返回 fileCategories + 合并后的通知字段；结果直接写入 vo ——
    private void llmExtractMulti(String combinedText, int fileCount, MeetingPrefillVO vo) throws Exception {
        DoubaoProperties.Llm llm = props.getLlm();
        String user = "当前年份：" + LocalDate.now().getYear()
                + "\n文件数量：" + fileCount + "（fileCategories 数组长度必须等于此数）\n\n各文件识别文字如下：\n" + combinedText;

        ObjectNode body = mapper.createObjectNode();
        body.put("model", llm.getModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", 1200);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", EXTRACT_MULTI_SYSTEM);
        messages.addObject().put("role", "user").put("content", user);
        body.putObject("response_format").put("type", "json_object");

        String url = trimTrailingSlash(llm.getBaseUrl()) + "/chat/completions";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(90))
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
        vo.setTokens(root.path("usage").path("total_tokens").asLong(0));

        int s = content.indexOf('{'), e = content.lastIndexOf('}');
        if (s < 0 || e <= s) { fillEmptyCategories(vo); return; }
        JsonNode n = mapper.readTree(content.substring(s, e + 1));
        vo.setTitle(n.path("title").asText("").trim());
        vo.setMeetingDate(n.path("meetingDate").asText("").trim());
        vo.setMeetingTime(n.path("meetingTime").asText("").trim());
        vo.setLocation(n.path("location").asText("").trim());
        vo.setConflictNote(n.path("conflict").asText("").trim());
        JsonNode topics = n.path("topics");
        if (topics.isArray()) {
            for (JsonNode t : topics) {
                String title = t.isObject() ? t.path("title").asText("") : t.asText("");
                if (title != null && !title.isBlank()) vo.getTopics().add(title.trim());
            }
        }
        JsonNode cats = n.path("fileCategories");
        if (cats.isArray()) {
            for (int i = 0; i < vo.getFiles().size() && i < cats.size(); i++) {
                String c = cats.get(i).asText("").trim().toLowerCase();
                vo.getFiles().get(i).setCategory("notice".equals(c) || "material".equals(c) ? c : "material");
            }
        }
        fillEmptyCategories(vo);
    }

    // 未拿到类别的文件兜底为 material（宁可当材料存起来，也不丢文件）。
    private void fillEmptyCategories(MeetingPrefillVO vo) {
        for (MeetingPrefillVO.FileInfo fi : vo.getFiles()) {
            if (fi.getCategory() == null || fi.getCategory().isBlank()) fi.setCategory("material");
        }
    }

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
