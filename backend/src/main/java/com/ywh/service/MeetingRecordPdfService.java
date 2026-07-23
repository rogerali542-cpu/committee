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
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        // 0722 放宽：测试期会议不真正归档(stage 保持 ongoing)，现场结束后即可导出；仅拦截尚未开始的会议
        if (meeting.getStage() == MeetingStage.preparing)
            throw new IllegalArgumentException("会议开始后才能导出会议记录");
        MeetingRecord record = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议记录不存在"));
        List<RecordAttendance> attendances = attendanceRepo.findByRecordId(record.getId());
        List<RecordTopic> topics = topicRepo.findByRecordIdOrderBySortOrder(record.getId());

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer w = new Writer(doc, loadChineseFont(doc));
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
            if (topics.isEmpty()) w.line("本次会议未登记议题。");
            int ti = 1;
            for (RecordTopic topic : topics) {
                w.subheading(ti++ + ". " + value(topic.getTitle()) + "【" + mergedTypeLabel(topic) + "】");
                if (usable(topic.getContent())) w.paragraph("议题说明：" + topic.getContent());
                List<TopicOpinion> opinions = opinionRepo.findByTopicId(topic.getId());
                if (!opinions.isEmpty()) {
                    w.line("讨论意见：");
                    for (TopicOpinion o : opinions) {
                        String speaker = o.getUserRole() != null ? o.getUserRole().getRealName() : o.getSpeakerName();
                        w.paragraph("• " + value(speaker) + "：" + value(o.getContent()));
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
                        w.line("• " + voter + "：" + choice);
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
            if (usable(record.getTodoListText())) w.paragraph(record.getTodoListText());
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

            w.close();
            doc.save(out);
            return new PdfFile(safe(meeting.getTitle()) + "-会议记录.pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("会议记录生成失败", e);
        }
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
    /** 对外类型名（0717 用户定：通知并入讨论）：notice/discussion 统一「通知和讨论」，其余沿用枚举 label；枚举值本身不动。 */
    private static String mergedTypeLabel(RecordTopic topic) {
        if (topic.getType() == null) return "未分类";
        String name = topic.getType().name();
        if ("notice".equals(name) || "discussion".equals(name)) return "通知和讨论";
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

    private static class Writer {
        private final PDDocument doc; private final PDFont font; private PDPage page; private PDPageContentStream cs;
        private float y; private final float left = 48, right = 48;
        Writer(PDDocument doc, PDFont font) throws IOException { this.doc = doc; this.font = font; newPage(); }
        void newPage() throws IOException { if (cs != null) cs.close(); page = new PDPage(PDRectangle.A4); doc.addPage(page); cs = new PDPageContentStream(doc, page); y = 795; }
        void ensure(float h) throws IOException { if (y - h < 55) newPage(); }
        void title(String s) throws IOException { center(s, 20); gap(8); }
        void center(String s, float size) throws IOException { ensure(24); float w=font.getStringWidth(s)/1000f*size; text(s, size, (page.getMediaBox().getWidth()-w)/2, y); y-=size+7; }
        void heading(String s) throws IOException { gap(8); ensure(28); text(s, 13, left, y); y-=22; }
        void subheading(String s) throws IOException { ensure(24); text(s, 11, left, y); y-=19; }
        void line(String s) throws IOException { for(String row:wrap(s, 10.5f, page.getMediaBox().getWidth()-left-right)){ ensure(18); text(row,10.5f,left,y); y-=17; } }
        void paragraph(String s) throws IOException { line(s); gap(3); }
        void signature(String name) throws IOException { ensure(35); text(name + "：____________________", 10.5f, left, y); y-=30; }
        void gap(float v) { y-=v; }
        void text(String s,float size,float x,float yy)throws IOException{cs.beginText();cs.setFont(font,size);cs.newLineAtOffset(x,yy);cs.showText(s==null?"":s);cs.endText();}
        List<String> wrap(String s,float size,float max)throws IOException{List<String> out=new ArrayList<>();for(String para:value(s).split("\\R",-1)){StringBuilder b=new StringBuilder();for(char c:para.toCharArray()){String n=b.toString()+c;if(font.getStringWidth(n)/1000f*size>max&&b.length()>0){out.add(b.toString());b.setLength(0);}b.append(c);}out.add(b.toString());}return out;}
        void close() throws IOException { if(cs!=null){cs.close();cs=null;} }
    }
}
