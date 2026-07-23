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

    @Transactional(readOnly = true)
    public PdfFile generate(Long meetingId) {
        RecordData d = loadRecordData(meetingId);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer w = new Writer(doc, loadChineseFont(doc));
            writeRecord(d, w);
            w.close();
            doc.save(out);
            return new PdfFile(safe(d.meeting().getTitle()) + "-会议记录.pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("会议记录生成失败", e);
        }
    }

    /** 会议记录纯文本（页内预览用）：与 PDF 走同一份 writeRecord 装配，保证所见即所导。 */
    @Transactional(readOnly = true)
    public String generateRecordText(Long meetingId) {
        RecordData d = loadRecordData(meetingId);
        TextSink t = new TextSink();
        try {
            writeRecord(d, t);
        } catch (IOException e) {
            throw new IllegalStateException("会议记录生成失败", e);
        }
        return t.text();
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

    /** 记录内容装配：PDF(Writer) 与文本预览(TextSink) 共用，改章节结构只改这里。 */
    private void writeRecord(RecordData d, Sink w) throws IOException {
        CommitteeMeeting meeting = d.meeting();
        List<RecordAttendance> attendances = d.attendances();
        w.title("业主委员会会议记录");
        String community = meeting.getCommunity() == null ? "" : meeting.getCommunity().getName();
        w.center((usable(community) ? community.trim() : "") + "业主委员会", 11);
        w.gap(16);
        w.line("会议名称：" + value(meeting.getTitle()));
        w.line("会议时间：" + value(meeting.getMeetingDate()) + " " +
                (meeting.getMeetingTime() == null ? "" : meeting.getMeetingTime().format(DateTimeFormatter.ofPattern("HH:mm"))));
        w.line("会议地点：" + value(meeting.getLocation()));
        int present = (int) attendances.stream().filter(a -> Boolean.TRUE.equals(a.getSignedIn())).count();
        w.line("应到委员：" + attendances.size() + "人    实到委员：" + present + "人");

        w.heading("一、参会情况");
        int ai = 1;
        for (RecordAttendance a : attendances) {
            if (a.getUserRole() == null) continue;
            String status = Boolean.TRUE.equals(a.getSignedIn()) ? "参会" : Boolean.TRUE.equals(a.getDeclined()) ? "请假" : "缺席";
            w.line(ai++ + ". " + value(a.getUserRole().getRealName()) + "（" + role(a) + "）  " + status);
        }

        w.heading("二、议题及过程记录");
        if (d.topics().isEmpty()) w.line("本次会议未登记议题。");
        int ti = 1;
        for (RecordTopic topic : d.topics()) {
            w.subheading(ti++ + ". " + value(topic.getTitle()) + "【" + mergedTypeLabel(topic) + "】");
            if (usable(topic.getContent())) w.paragraph("议题说明：" + topic.getContent());
            List<TopicOpinion> opinions = opinionRepo.findByTopicId(topic.getId());
            if (!opinions.isEmpty()) {
                w.line("讨论意见：");
                for (TopicOpinion o : opinions) {
                    String speaker = o.getUserRole() != null ? o.getUserRole().getRealName() : o.getSpeakerName();
                    // 项目符号用中点「·」：西文 •(U+2022) 在 SimHei 等中文字体里没有字形，会整份导出报错
                    w.paragraph("· " + value(speaker) + "：" + value(o.getContent()));
                }
            }
            List<TopicVote> votes = voteRepo.findByTopicId(topic.getId());
            if (!votes.isEmpty()) {
                w.line("表决记录：");
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (TopicVote v : votes) {
                    String choice = v.getChoice() == null ? "未表决" : v.getChoice().getLabel();
                    counts.merge(choice, 1, Integer::sum);
                    String voter = v.getUserRole() == null ? "未知委员" : v.getUserRole().getRealName();
                    w.line("· " + voter + "：" + choice);
                }
                w.line("表决汇总：" + counts.entrySet().stream()
                        .map(e -> e.getKey() + e.getValue() + "票").reduce((a, b) -> a + "，" + b).orElse("无"));
            } else if (topic.getType() != null
                    && !"notice".equals(topic.getType().name())
                    && !"discussion".equals(topic.getType().name())) {
                // 通知和讨论类不表决，没投票记录是常态，不打「未记录表决结果」（0717 随类型合并修正：原先只豁免 notice）
                w.line("结果：未记录表决结果");
            }
            w.gap(6);
        }

        w.heading("三、会议决定及后续事项");
        if (usable(d.record().getTodoListText())) w.paragraph(d.record().getTodoListText());
        else w.line("无已登记的会后待办事项。");

        w.heading("四、签字确认");
        w.paragraph("以上记录经出席委员核对无误，由出席委员统一签字确认。");
        List<RecordAttendance> presentRows = attendances.stream().filter(a -> Boolean.TRUE.equals(a.getSignedIn())).toList();
        for (RecordAttendance a : presentRows) {
            w.signature(value(a.getUserRole() == null ? null : a.getUserRole().getRealName()));
        }
        w.gap(12);
        w.line("主持人签字：____________________");
        w.gap(14);
        w.line("日期：________年____月____日                 业主委员会盖章：");
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
            for (String raw : minutesText.split("\\R")) {
                String t = raw.strip().replaceAll("^#{1,6}\\s*", ""); // 去掉 AI 稿里的 Markdown 标题记号
                if (t.isEmpty()) { w.gap(6); continue; }
                if (t.equals("会议纪要") || t.equals(meetingTitle) || t.equals(docTitle)) continue; // 开头标题行不重复
                w.paragraph("　　" + t);
            }
            w.close();
            doc.save(out);
            return new PdfFile(safe(meeting.getTitle()) + "-会议纪要.pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("会议纪要生成失败", e);
        }
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

    /** 记录内容的输出端：PDF(Writer) 与纯文本(TextSink) 各自实现，writeRecord 只写一份。 */
    private interface Sink {
        void title(String s) throws IOException;
        void center(String s, float size) throws IOException;
        void heading(String s) throws IOException;
        void subheading(String s) throws IOException;
        void line(String s) throws IOException;
        void paragraph(String s) throws IOException;
        void signature(String name) throws IOException;
        void gap(float v) throws IOException;
    }

    /** 纯文本输出：标题/章节间以空行分隔，供前端页内预览排版。 */
    private static class TextSink implements Sink {
        private final StringBuilder sb = new StringBuilder();
        private void add(String s) { sb.append(s == null ? "" : s).append('\n'); }
        public void title(String s) { add(s); }
        public void center(String s, float size) { add(s); }
        public void heading(String s) { add(""); add(s); }
        public void subheading(String s) { add(s); }
        public void line(String s) { add(s); }
        public void paragraph(String s) { add(s); }
        public void signature(String name) { add(name + "：____________________"); }
        public void gap(float v) { if (v >= 10) add(""); }
        String text() { return sb.toString().strip(); }
    }

    private static class Writer implements Sink {
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
        public void signature(String name) throws IOException { ensure(35); text(name + "：____________________", 10.5f, left, y); y-=30; }
        public void gap(float v) { y-=v; }
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
