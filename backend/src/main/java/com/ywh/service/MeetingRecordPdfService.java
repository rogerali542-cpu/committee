package com.ywh.service;

import com.ywh.entity.*;
import com.ywh.enums.MeetingStage;
import com.ywh.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MeetingRecordPdfService {
    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingRecordRepository recordRepo;
    private final RecordAttendanceRepository attendanceRepo;
    private final RecordTopicRepository topicRepo;
    private final TopicOpinionRepository opinionRepo;
    private final TopicVoteRepository voteRepo;
    private final MeetingPublishRepository publishRepo;

    @Transactional(readOnly = true)
    public PdfFile generate(Long meetingId) {
        RecordContent c = buildRecordContent(loadRecordData(meetingId));
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer w = new Writer(doc, loadChineseFont(doc));
            renderRecordForm(c, w);
            w.close();
            doc.save(out);
            return new PdfFile(safe(c.meetingTitle) + "-会议记录.pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("会议记录生成失败", e);
        }
    }

    /** 会议记录纯文本（页内预览用）：与 PDF 同一份 buildRecordContent 装配，保证所见即所导。 */
    @Transactional(readOnly = true)
    public String generateRecordText(Long meetingId) {
        return renderRecordText(buildRecordContent(loadRecordData(meetingId)));
    }

    private RecordData loadRecordData(Long meetingId) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        // 0722 放宽：测试期会议不真正归档(stage 保持 ongoing)，现场结束后即可导出；仅拦截尚未开始的会议
        if (meeting.getStage() == MeetingStage.preparing)
            throw new IllegalArgumentException("会议开始后才能导出会议记录");
        MeetingRecord record = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议记录不存在"));
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());
        return new RecordData(meeting, record, attendances, topics);
    }

    /** 会议记录内容（0723 向真实《业主委员会工作手册》表格看齐）：PDF 表单与文本预览共用。 */
    private static class RecordContent {
        String meetingTitle;                       // 文件名用
        String org;                                // 小区业委会（居中行）
        List<String[]> infoRows = new ArrayList<>(); // 表头行：label,value 交替
        List<String> content = new ArrayList<>();    // 会议内容
        List<String> decisions = new ArrayList<>();  // 会议有关决定及表决结果（主表：票数概要）
        List<String> resultAppendix = new ArrayList<>(); // 会议结果附页（逐题详细表决明细，对应主表"另附"）
        String noticeTime;                           // 会议决定、决议公告的时间
        List<String> attendees = new ArrayList<>();  // 出席成员（签章格）
    }

    private static final String[] CN_NUM = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static String cnNum(int i) { return i >= 1 && i <= 10 ? CN_NUM[i] : String.valueOf(i); }

    private RecordContent buildRecordContent(RecordData d) {
        CommitteeMeeting meeting = d.meeting();
        List<RecordAttendance> attendances = d.attendances();
        List<RecordAttendance> present = attendances.stream().filter(a -> Boolean.TRUE.equals(a.getSignedIn())).toList();
        RecordContent c = new RecordContent();
        c.meetingTitle = value(meeting.getTitle());
        String community = meeting.getCommunity() == null ? "" : meeting.getCommunity().getName();
        c.org = (usable(community) ? community.trim() : "") + "业主委员会";

        String host = attendances.stream()
                .filter(a -> a.getUserRole() != null && a.getUserRole().getRole() != null && a.getUserRole().getRole().isChair())
                .findFirst().map(a -> a.getUserRole().getRealName())
                .orElse(present.isEmpty() ? "" : present.get(0).getUserRole().getRealName());
        String time = value(meeting.getMeetingDate()) + " " +
                (meeting.getMeetingTime() == null ? "" : meeting.getMeetingTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        boolean online = meeting.getMeetingMethod() == com.ywh.enums.MeetingMethod.online;
        String place = usable(meeting.getLocation()) ? meeting.getLocation() : (online ? "微信工作群" : "未记录");
        if (online && usable(meeting.getLocation())) place = meeting.getLocation() + "（线上）";
        // 实到写「N+M」＝委员+列席（真实手写表惯例：7+3）
        String observers = d.record().getObserversText();
        int obsCount = !usable(observers) ? 0 : observers.trim().split("[、，,\\s]+").length;
        String presentText = obsCount > 0 ? present.size() + "+" + obsCount + "（委员" + present.size() + "人，列席" + obsCount + "人）"
                : String.valueOf(present.size());

        c.infoRows.add(new String[]{"会议议题", c.meetingTitle, "时  间", time, "主持人", host});
        c.infoRows.add(new String[]{"会议地址", place, "记录人", host});
        c.infoRows.add(new String[]{"应到人数", String.valueOf(attendances.size()), "实到人数", presentText});

        // ── 会议内容：列席、到会情况与议题过程 ──
        if (obsCount > 0) c.content.add("列席人员：" + observers.trim() + "。");
        List<RecordAttendance> absent = attendances.stream().filter(a -> !Boolean.TRUE.equals(a.getSignedIn())).toList();
        if (!absent.isEmpty()) {
            c.content.add("缺席委员：" + absent.stream()
                    .map(a -> value(a.getUserRole() == null ? null : a.getUserRole().getRealName())
                            + (Boolean.TRUE.equals(a.getDeclined()) ? "（请假）" : ""))
                    .reduce((a, b) -> a + "、" + b).orElse("") + "。");
        }
        if (d.topics().isEmpty()) c.content.add("本次会议未登记议题。");
        int ti = 1;
        for (RecordTopic topic : d.topics()) {
            c.content.add(ti++ + ". " + value(topic.getTitle()) + "【" + mergedTypeLabel(topic) + "】");
            if (usable(topic.getContent())) c.content.add("议题说明：" + topic.getContent());
            List<TopicOpinion> opinions = opinionRepo.findByTopicId(topic.getId());
            for (TopicOpinion o : opinions) {
                String speaker = o.getUserRole() != null ? o.getUserRole().getRealName() : o.getSpeakerName();
                // 项目符号用中点「·」：西文 •(U+2022) 在 SimHei 等中文字体里没有字形，会整份导出报错
                c.content.add("· " + value(speaker) + "：" + value(o.getContent()));
            }
        }

        // ── 会议有关决定及表决结果：主表写票数概要，逐题详细（同意/不同意/弃权委员名单）另附《会议结果》一页 ──
        int di = 1;
        boolean anyVote = false;
        for (RecordTopic topic : d.topics()) {
            String title = value(topic.getTitle());
            List<TopicVote> votes = voteRepo.findByTopicId(topic.getId());
            if (!votes.isEmpty()) {
                anyVote = true;
                Map<String, Integer> counts = new LinkedHashMap<>();
                Map<String, List<String>> byChoice = new LinkedHashMap<>();
                for (TopicVote v : votes) {
                    String choice = v.getChoice() == null ? "未表决" : v.getChoice().getLabel();
                    counts.merge(choice, 1, Integer::sum);
                    byChoice.computeIfAbsent(choice, k -> new ArrayList<>())
                            .add(v.getUserRole() == null ? "未知委员" : v.getUserRole().getRealName());
                }
                String tally = counts.entrySet().stream()
                        .map(e -> e.getKey() + e.getValue() + "票").reduce((a, b) -> a + "，" + b).orElse("无");
                // 主表：票数概要一行
                c.decisions.add(di + ". " + title + "：" + tally + "。");
                // 附页：逐题详细表决明细
                c.resultAppendix.add(cnNum(di) + "、" + title + "【" + mergedTypeLabel(topic) + "】");
                c.resultAppendix.add("　　表决情况：" + tally + "。");
                c.resultAppendix.add("　　同意的委员：" + String.join("、", byChoice.getOrDefault("同意", List.of("（无）"))) + "。");
                c.resultAppendix.add("　　不同意的委员：" + String.join("、", byChoice.getOrDefault("反对", List.of("（无）"))) + "。");
                if (byChoice.containsKey("弃权"))
                    c.resultAppendix.add("　　弃权的委员：" + String.join("、", byChoice.get("弃权")) + "。");
            } else if (topic.getType() == null
                    || "notice".equals(topic.getType().name()) || "discussion".equals(topic.getType().name())) {
                String kind = topic.getType() != null && "discussion".equals(topic.getType().name())
                        ? "有关意见已在会议中讨论并记录" : "有关情况已在会议中通报";
                c.decisions.add(di + ". " + title + "：" + kind + "。");
                c.resultAppendix.add(cnNum(di) + "、" + title + "【" + mergedTypeLabel(topic) + "】：" + kind + "。");
            } else {
                c.decisions.add(di + ". " + title + "：未记录表决结果。");
                c.resultAppendix.add(cnNum(di) + "、" + title + "：未记录表决结果。");
            }
            di++;
        }
        // 待办事项不进档案文书（0723 用户定：待办/新闻稿是本产品增值功能，与记录/纪要/公示体系无关）
        if (c.decisions.isEmpty()) {
            c.decisions.add(anyVote ? "无" : "本次会议未形成需表决的决定事项。");
        } else if (anyVote) {
            c.decisions.add("各议题详细表决情况及委员表决记录见附页《会议结果》。");
        }

        // ── 会议决定、决议公告的时间：公示后自动回填（真实手写表须人工补记，这里系统代劳） ──
        MeetingPublish pub = publishRepo.findByMeetingId(meeting.getId()).orElse(null);
        c.noticeTime = pub != null && Boolean.TRUE.equals(pub.getPublished()) && pub.getPublishDate() != null
                ? pub.getPublishDate() + "，会议纪要及有关事项进行了公示"
                : "（待公示后回填）";

        for (RecordAttendance a : present) {
            c.attendees.add(value(a.getUserRole() == null ? null : a.getUserRole().getRealName()));
        }
        return c;
    }

    /** PDF：仿《业主委员会工作手册》表格版式——表头格、内容区、决定区、公告时间行、签章格。 */
    private void renderRecordForm(RecordContent c, Writer w) throws IOException {
        w.title("业主委员会会议记录");
        w.center(c.org, 11);
        w.gap(12);
        w.formRow(c.infoRows.get(0), new float[]{0.95f, 2.4f, 0.7f, 1.5f, 0.75f, 0.9f});
        w.formRow(c.infoRows.get(1), new float[]{0.95f, 4.0f, 0.75f, 1.5f});
        w.formRow(c.infoRows.get(2), new float[]{0.95f, 2.65f, 0.95f, 2.65f});
        w.gap(10);
        w.heading("会议内容：");
        for (String s : c.content) w.line(s);
        w.gap(6); w.hr();
        w.heading("会议有关决定及表决结果（会议结果另附）：");
        for (String s : c.decisions) w.line(s);
        w.gap(6); w.hr();
        w.formRow(new String[]{"会议决定、决议公告的时间", c.noticeTime}, new float[]{2.0f, 5.2f});
        w.gap(12);
        w.center("出席成员名单及签章", 11);
        w.gap(4);
        w.signGrid(c.attendees, 5);
        // ── 会议结果附页（独立一页，对应主表"会议结果另附"）──
        if (!c.resultAppendix.isEmpty()) {
            w.newPage();
            w.title("会议结果");
            w.center(c.org + "（会议记录附页）", 10.5f);
            w.gap(14);
            for (String s : c.resultAppendix) {
                if (s.matches("^[一二三四五六七八九十\\d]+、.*")) w.subheading(s); // 议题标题行加粗
                else w.line(s);
            }
            w.gap(16);
            w.line("以上表决情况经出席委员核对无误。");
            w.gap(10);
            w.center("出席委员签字", 11);
            w.gap(4);
            w.signGrid(c.attendees, 5);
        }
    }

    /** 纯文本（页内预览）：同一份内容按行排出，前端按标签行加粗。 */
    private String renderRecordText(RecordContent c) {
        StringBuilder sb = new StringBuilder();
        sb.append("业主委员会会议记录\n").append(c.org).append("\n\n");
        for (String[] row : c.infoRows) {
            List<String> pairs = new ArrayList<>();
            for (int i = 0; i + 1 < row.length; i += 2) pairs.add(row[i].replace("  ", "") + "：" + row[i + 1]);
            sb.append(String.join("    ", pairs)).append('\n');
        }
        sb.append("\n会议内容：\n");
        for (String s : c.content) sb.append(s).append('\n');
        sb.append("\n会议有关决定及表决结果（会议结果另附）：\n");
        for (String s : c.decisions) sb.append(s).append('\n');
        sb.append("\n会议决定、决议公告的时间：").append(c.noticeTime).append('\n');
        sb.append("\n出席成员名单及签章：\n");
        sb.append(c.attendees.isEmpty() ? "（无出席记录）" : String.join("、", c.attendees));
        sb.append("\n（打印后由出席委员在签章格内签字）\n");
        if (!c.resultAppendix.isEmpty()) {
            sb.append("\n附页：会议结果\n");
            for (String s : c.resultAppendix) sb.append(s).append('\n');
            sb.append("以上表决情况经出席委员核对无误。（打印后由出席委员签字）\n");
        }
        return sb.toString().strip();
    }

    /** 会议纪要 PDF：纪要正文（大模型/人工编辑稿）按公文格式落页——标题居中 + 每段首行缩进两格。 */
    @Transactional(readOnly = true)
    public PdfFile generateMinutesPdf(Long meetingId, String minutesText) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (minutesText == null || minutesText.isBlank())
            throw new IllegalArgumentException("还没有纪要正文，请先生成会议纪要");
        String meetingTitle = value(meeting.getTitle());
        // 标题去重：会议名以「会议」结尾时避免拼成「××会议会议纪要」（与前端 joinMinutesTitle 同规则）
        String docTitle = meetingTitle.replaceAll("会议纪要$", "").replaceAll("会议$", "") + "会议纪要";
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer w = new Writer(doc, loadChineseFont(doc));
            w.title(docTitle);
            w.gap(10);
            boolean bodyStarted = false;
            for (String raw : minutesText.split("\\R")) {
                String t = raw.strip().replaceAll("^#{1,6}\\s*", ""); // 去掉 AI 稿里的 Markdown 标题记号
                if (t.isEmpty()) { if (bodyStarted) w.gap(6); continue; }
                // 正文开头的标题行不重复（页首已排 docTitle）：含 AI 稿自带的「××业委会会议纪要」
                if (!bodyStarted && (t.equals("会议纪要") || t.equals(meetingTitle) || t.equals(docTitle)
                        || (t.length() <= 30 && t.endsWith("会议纪要")))) continue;
                bodyStarted = true;
                if (isSignoffLine(t)) { w.right(t); continue; } // 落款（业委会全称/日期）右对齐，仿真实公文
                w.paragraph("　　" + t);
            }
            // 盖章位（0723 用户定：只留位、不做电子章）——落款下方标注并留白，打印后线下加盖公章
            w.gap(6);
            w.right("（盖章）");
            w.gap(36);
            w.close();
            doc.save(out);
            return new PdfFile(safe(meeting.getTitle()) + "-会议纪要.pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("会议纪要生成失败", e);
        }
    }

    /** 落款行判定：业委会全称（可带届别）或日期行，右对齐排版。 */
    private static boolean isSignoffLine(String t) {
        return t.matches(".{0,30}业主委员会(（第.{1,6}届）)?") || t.matches("\\d{4}年\\d{1,2}月\\d{1,2}日");
    }

    private String role(RecordAttendance a) {
        if (a.getUserRole().getRole() == null) return "委员";
        String n = a.getUserRole().getRole().name();
        return "主任".equals(n) || "副主任".equals(n) ? n : "委员";
    }
    private static boolean usable(String s) { return s != null && !s.isBlank() && !s.matches("[?？\\s]+") && !s.contains("锟"); }
    private static String value(Object o) { return o == null || String.valueOf(o).isBlank() ? "未记录" : String.valueOf(o); }
    /** 对外类型名（0722 用户定：类型写清楚）：notice=通知、discussion=讨论，其余沿用枚举 label。 */
    private static String mergedTypeLabel(RecordTopic topic) {
        if (topic.getType() == null) return "未分类";
        String name = topic.getType().name();
        if ("notice".equals(name)) return "通知";
        if ("discussion".equals(name)) return "讨论";
        return topic.getType().getLabel();
    }
    private static String safe(String s) { return (usable(s) ? s : "会议").replaceAll("[\\\\/:*?\"<>|]", "_"); }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        String[] paths = {"C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/simsun.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"};
        for (String path : paths) {
            File f = new File(path); if (!f.isFile()) continue;
            if (path.endsWith(".ttf")) return PDType0Font.load(document, f);
            try (TrueTypeCollection c = new TrueTypeCollection(f)) {
                PDFont[] found = new PDFont[1];
                c.processAllFonts(ttf -> { if (found[0] == null) found[0] = PDType0Font.load(document, ttf, true); });
                if (found[0] != null) return found[0];
            }
        }
        throw new IllegalStateException("服务器缺少中文字体，无法生成会议记录");
    }

    public record PdfFile(String fileName, byte[] bytes) {}

    private record RecordData(CommitteeMeeting meeting, MeetingRecord record,
                              List<RecordAttendance> attendances, List<RecordTopic> topics) {}

    private static class Writer {
        private final PDDocument doc; private final PDFont font; private PDPage page; private PDPageContentStream cs;
        private float y; private final float left = 48, right = 48;
        private final Map<Integer, String> glyphCache = new HashMap<>(); // 码点→可渲染替代，逐字校验的结果缓存
        Writer(PDDocument doc, PDFont font) throws IOException { this.doc = doc; this.font = font; newPage(); }
        void newPage() throws IOException { if (cs != null) cs.close(); page = new PDPage(PDRectangle.A4); doc.addPage(page); cs = new PDPageContentStream(doc, page); y = 795; }
        void ensure(float h) throws IOException { if (y - h < 55) newPage(); }
        public void title(String s) throws IOException { center(s, 20); gap(8); }
        public void center(String s, float size) throws IOException { s = sanitize(s); ensure(24); float w=font.getStringWidth(s)/1000f*size; text(s, size, (page.getMediaBox().getWidth()-w)/2, y); y-=size+7; }
        public void heading(String s) throws IOException { gap(8); ensure(28); text(s, 13, left, y); y-=22; }
        public void subheading(String s) throws IOException { ensure(24); text(s, 11, left, y); y-=19; }
        public void line(String s) throws IOException { for(String row:wrap(s, 10.5f, page.getMediaBox().getWidth()-left-right)){ ensure(18); text(row,10.5f,left,y); y-=17; } }
        public void paragraph(String s) throws IOException { line(s); gap(3); }
        /** 右对齐行：落款（业委会全称/日期）用，仿真实公文靠右落款。 */
        public void right(String s) throws IOException { s = sanitize(s); ensure(20); float w=font.getStringWidth(s)/1000f*10.5f; text(s, 10.5f, page.getMediaBox().getWidth()-right-w-30, y); y-=17; }
        public void signature(String name) throws IOException { ensure(35); text(name + "：____________________", 10.5f, left, y); y-=30; }
        public void gap(float v) { y-=v; }
        float usableW() { return page.getMediaBox().getWidth() - left - right; }
        /** 通栏横线（表格分区） */
        void hr() throws IOException { ensure(10); cs.setLineWidth(0.8f); cs.moveTo(left, y); cs.lineTo(left + usableW(), y); cs.stroke(); y -= 10; }
        /** 表格行：cells 依序排满一行，widths 为各格宽度权重；文字自动换行，行高取最高格。 */
        void formRow(String[] cells, float[] weights) throws IOException {
            float total = 0; for (float f : weights) total += f;
            float[] ws = new float[weights.length];
            for (int i = 0; i < weights.length; i++) ws[i] = usableW() * weights[i] / total;
            List<List<String>> wrapped = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                List<String> ls = wrap(cells[i] == null ? "" : cells[i], 10.5f, ws[i] - 10);
                wrapped.add(ls);
                maxLines = Math.max(maxLines, ls.size());
            }
            float h = Math.max(26, maxLines * 15 + 11);
            ensure(h + 4);
            cs.setLineWidth(0.8f);
            float x = left;
            for (int i = 0; i < cells.length; i++) {
                cs.addRect(x, y - h, ws[i], h); cs.stroke();
                float ty = y - 17;
                for (String ln : wrapped.get(i)) { text(ln, 10.5f, x + 5, ty); ty -= 15; }
                x += ws[i];
            }
            y -= h;
        }
        /** 签章格：每行 cols 格，格内左上印姓名、留白供签字（仿工作手册「出席成员名单及签章」）。 */
        void signGrid(List<String> names, int cols) throws IOException {
            float cw = usableW() / cols, ch = 46;
            int rows = Math.max(1, (int) Math.ceil(names.size() / (double) cols));
            cs.setLineWidth(0.8f);
            for (int r = 0; r < rows; r++) {
                ensure(ch + 4);
                float x = left;
                for (int cIdx = 0; cIdx < cols; cIdx++) {
                    int idx = r * cols + cIdx;
                    cs.addRect(x, y - ch, cw, ch); cs.stroke();
                    if (idx < names.size()) text(names.get(idx), 9.5f, x + 5, y - 14);
                    x += cw;
                }
                y -= ch;
            }
        }
        void text(String s,float size,float x,float yy)throws IOException{cs.beginText();cs.setFont(font,size);cs.newLineAtOffset(x,yy);cs.showText(sanitize(s));cs.endText();}
        List<String> wrap(String s,float size,float max)throws IOException{List<String> out=new ArrayList<>();for(String para:sanitize(value(s)).split("\\R",-1)){StringBuilder b=new StringBuilder();for(char c:para.toCharArray()){String n=b.toString()+c;if(font.getStringWidth(n)/1000f*size>max&&b.length()>0){out.add(b.toString());b.setLength(0);}b.append(c);}out.add(b.toString());}return out;}
        void close() throws IOException { if(cs!=null){cs.close();cs=null;} }

        /** 字形兜底：正文来自用户/AI 文本，含中文字体没有的字符（如 • 或 emoji）时逐字降级，不再整份报错。 */
        private String sanitize(String s) {
            if (s == null || s.isEmpty()) return "";
            StringBuilder out = new StringBuilder(s.length());
            s.codePoints().forEach(cp -> out.append(glyphCache.computeIfAbsent(cp, this::renderable)));
            return out.toString();
        }
        private String renderable(int cp) {
            // 常见西文排版符先做等义替换（中文字体普遍缺这些字形）
            String ch = switch (cp) {
                case 0x2022, 0x25E6, 0x2219 -> "·";   // 项目符号 • ◦ ∙ → 中点
                case 0x2028, 0x2029 -> "\n";           // Unicode 行分隔符 → 换行
                default -> new String(Character.toChars(cp));
            };
            if (ch.equals("\n")) return ch; // 换行不落字形，交给 wrap 分段
            try { font.getStringWidth(ch); return ch; }
            catch (Exception e) {
                try { font.getStringWidth("□"); return "□"; } catch (Exception e2) { return "?"; }
            }
        }
    }
}
