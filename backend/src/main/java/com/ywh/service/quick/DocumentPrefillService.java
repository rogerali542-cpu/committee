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
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 【仅演示】固定素材假识别开关。默认 false（走真实 OCR+大模型）；
     * 本地演示时在 application.yml 置 demo.prefill.enabled=true：跳过真实识别，
     * 按文件名判类、套用预置的会议通知内容、模拟 10~15s 耗时与 token，
     * 便于用固定样本（材料1_会议通知 / 材料2_电梯维保 / 材料3_车库照明）稳定走查流程。
     * 文件仍由控制器照常落库拿 fileUrl，材料可正常挂载传阅。上线务必保持 false。
     */
    @Value("${demo.prefill.enabled:false}")
    private boolean demoPrefill;

    // —— 演示预置：材料1_会议通知 抽取出的会议信息（真实大模型也会抽成这些字段）——
    private static final String DEMO_TITLE = "2026年第3次业主委员会例会";
    private static final String DEMO_DATE = "2026-06-29";
    private static final String DEMO_TIME = "10:00";
    private static final String DEMO_LOCATION = "社区活动室";
    private static final List<String> DEMO_TOPICS = List.of(
            "审议小区电梯年度维保方案",
            "审议地下车库照明改造预算",
            "讨论第三季度物业费调整事项");

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
            二、把 notice 文件按“是否同一场会议”归并：同一份通知被拆成多页/多张照片的合并为一份；会议名称/时间/地点/议题明显不同的各算一份。在 notices 数组里逐份输出每份通知的字段（仅依据原文、不得编造；material 不参与）。
            三、顶层 title/meetingDate/meetingTime/location/topics 填最主要的一份（通常取 notices 的第一份）。
            四、若 notices 有两份及以上（识别到多份不同的通知），conflict 用一句话说明（如“识别到多份不同的会议通知”）；只有一份或没有通知则 conflict 为空字符串 ""。
            只输出一个 JSON 对象，不要任何解释、不要 Markdown 代码块：
            {"fileCategories":["notice或material", ...],"notices":[{"title":"会议名称","meetingDate":"YYYY-MM-DD","meetingTime":"HH:mm","location":"会议地点","topics":["议题标题1"]}],"title":"会议名称","meetingDate":"YYYY-MM-DD","meetingTime":"HH:mm","location":"会议地点","topics":["议题标题1","议题标题2"],"conflict":"冲突说明或空串"}
            要求：
            1. 只从给定文字提取；缺失的字段用空字符串 ""，topics 缺失用空数组 []，不要编造。
            2. 日期归一化为 YYYY-MM-DD；只有“X月X日”没有年份时用我给的“当前年份”。
            3. 时间归一化为 24 小时制 HH:mm（如“下午两点半”→“14:30”）。
            4. topics 只取会议要讨论/审议/表决的议题标题，简洁，一条一个。
            5. 若没有任何 notice 文件，notices 为空数组 []、title/meetingDate/meetingTime/location 留空、topics 留空数组，fileCategories 照常逐个给出，conflict 为空串。
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
        if (demoPrefill) return demoParseMulti(docs);
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

    /**
     * 【仅演示】固定素材假识别：不调 OCR/大模型，按文件名判「通知/材料」，
     * 通知套用预置会议信息，模拟 10~15s 真实耗时与合理 token 数。
     * 文件由控制器照常落库拿 fileUrl（材料可挂载传阅），与真实流程表现一致。
     */
    private MeetingPrefillVO demoParseMulti(List<Doc> docs) {
        log.info("[PREFILL-DEMO] 演示模式假识别（demo.prefill.enabled=true），共 {} 个文件", docs.size());
        MeetingPrefillVO vo = new MeetingPrefillVO();
        long tokens = 0;
        boolean noticeFilled = false;
        for (Doc d : docs) {
            MeetingPrefillVO.FileInfo fi = new MeetingPrefillVO.FileInfo();
            fi.setFileName(d.filename() == null ? "" : d.filename());
            fi.setFileType(d.fileType() == null ? "" : d.fileType());
            fi.setFileSize(d.fileSize());
            if (looksLikeNotice(d.filename())) {
                fi.setCategory("notice");
                if (!noticeFilled) {   // 只用第一份通知预填顶层字段
                    vo.setTitle(DEMO_TITLE);
                    vo.setMeetingDate(DEMO_DATE);
                    vo.setMeetingTime(DEMO_TIME);
                    vo.setLocation(DEMO_LOCATION);
                    vo.getTopics().addAll(DEMO_TOPICS);
                    noticeFilled = true;
                }
                tokens += 1280;
            } else {
                fi.setCategory("material");
                tokens += 860 + Math.round(Math.random() * 200);
            }
            vo.getFiles().add(fi);
        }
        vo.setTokens(tokens);
        vo.setAvailable(true);
        vo.setCategory(noticeFilled ? "notice" : "material");
        vo.setMessage(noticeFilled ? "已识别并预填，请核对" : "已识别为会议材料");
        // 模拟真实 OCR + 大模型耗时：10~15 秒（前端据此显示进度与用时）
        try {
            Thread.sleep(10000 + (long) (Math.random() * 5000));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return vo;
    }

    // 演示判类：文件名含「通知/notice」即当会议通知，其余当会议材料。
    private boolean looksLikeNotice(String filename) {
        if (filename == null) return false;
        String n = filename.toLowerCase();
        return n.contains("通知") || n.contains("notice");
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
        // 逐份通知（供前端在"多份不同通知"时择一）
        JsonNode notices = n.path("notices");
        if (notices.isArray()) {
            for (JsonNode nn : notices) {
                MeetingPrefillVO.NoticeOption opt = new MeetingPrefillVO.NoticeOption();
                opt.setTitle(nn.path("title").asText("").trim());
                opt.setMeetingDate(nn.path("meetingDate").asText("").trim());
                opt.setMeetingTime(nn.path("meetingTime").asText("").trim());
                opt.setLocation(nn.path("location").asText("").trim());
                JsonNode tp = nn.path("topics");
                if (tp.isArray()) {
                    for (JsonNode t : tp) {
                        String tt = t.isObject() ? t.path("title").asText("") : t.asText("");
                        if (tt != null && !tt.isBlank()) opt.getTopics().add(tt.trim());
                    }
                }
                // 至少有个会议名称或时间/地点才算有效一份
                if (!opt.getTitle().isEmpty() || !opt.getMeetingDate().isEmpty()
                        || !opt.getMeetingTime().isEmpty() || !opt.getLocation().isEmpty()) {
                    vo.getNotices().add(opt);
                }
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
