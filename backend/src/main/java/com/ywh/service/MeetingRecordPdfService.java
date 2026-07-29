package com.ywh.service;

import com.ywh.entity.*;
import com.ywh.enums.MeetingStage;
import com.ywh.enums.VoteChoice;
import com.ywh.dto.quick.QuickConfirmRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final ObjectMapper objectMapper;

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

    /** 现场汇总票数（会后整理确认的票数，与逐人投票并存）：topicId → 结果。解析失败按无汇总处理。 */
    private Map<Long, QuickConfirmRequest.TopicResult> quickConfirmMap(MeetingRecord record) {
        Map<Long, QuickConfirmRequest.TopicResult> map = new HashMap<>();
        if (record == null || record.getQuickConfirmJson() == null || record.getQuickConfirmJson().isBlank()) return map;
        try {
            QuickConfirmRequest req = objectMapper.readValue(record.getQuickConfirmJson(), QuickConfirmRequest.class);
            if (req.getTopics() != null)
                for (QuickConfirmRequest.TopicResult t : req.getTopics())
                    if (t != null && t.getTopicId() != null) map.putIfAbsent(t.getTopicId(), t);
        } catch (Exception e) { /* 容错：无/坏 JSON 时按无现场汇总处理 */ }
        return map;
    }

    /** 多选议题的选项列表（[{id,label}, ...]）：解析失败返回空。 */
    private List<Map<String, Object>> parseOptions(RecordTopic topic) {
        if (topic.getOptionsJson() == null || topic.getOptionsJson().isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(topic.getOptionsJson(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o).trim()); } catch (Exception e) { return null; }
    }

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
            // 只有「讨论」类议题才逐条记录发言；「表决」类的结论在下方"决定及表决结果"用票数概括，
            // 不再把每人的投票附言（多是"我同意…"）重复列进会议内容——避免同一人同一态度写两三遍。
            boolean isDiscussion = topic.getType() != null && "discussion".equals(topic.getType().name());
            if (isDiscussion) {
                List<TopicOpinion> opinions = opinionRepo.findByTopicId(topic.getId());
                for (TopicOpinion o : opinions) {
                    String speaker = o.getUserRole() != null ? o.getUserRole().getRealName() : o.getSpeakerName();
                    // 项目符号用中点「·」：西文 •(U+2022) 在 SimHei 等中文字体里没有字形，会整份导出报错
                    c.content.add("· " + value(speaker) + "：" + value(o.getContent()));
                }
            }
        }

        // ── 会议有关决定及表决结果：主表逐题写"同意/反对/弃权"票数与表决结果；委员名单另附《会议结果》一页 ──
        // 票数口径与 App 的议题详情完全一致：逐人投票(含代委员投票)与"现场汇总票数"(quickConfirmJson)逐桶取大，
        // 过半数(应到/2+1)即通过。此前只数逐人票、且附页按错误的"同意"标签取名单(实际标签是"赞成")，导致名单恒为空。
        // 表决过半口径与 App 议题详情完全一致：按"实到（已签到）人数"过半，而非应到——
        // 否则记录写的通过/未通过会与 App 里议题卡显示的结果打架。
        int need = present.size() / 2 + 1;
        Map<Long, QuickConfirmRequest.TopicResult> quickMap = quickConfirmMap(d.record());
        int di = 1;
        boolean anyVote = false;
        for (RecordTopic topic : d.topics()) {
            String title = value(topic.getTitle());
            List<TopicVote> votes = voteRepo.findByTopicId(topic.getId());
            QuickConfirmRequest.TopicResult qr = quickMap.get(topic.getId());
            boolean multi = "multi_choice".equals(topic.getDecisionType());
            boolean qrHasSimple = qr != null && (qr.getForVotes() != null || qr.getAgVotes() != null || qr.getAbVotes() != null);
            boolean qrHasMulti = qr != null && qr.getOptionVotes() != null && !qr.getOptionVotes().isEmpty();
            boolean hasVotes = !votes.isEmpty() || qrHasSimple || qrHasMulti;

            if (hasVotes && multi) {
                anyVote = true;
                // 多选表决：逐人 selectedId 与现场汇总 optionVotes 逐桶取大，得票最多的选项过半即通过
                Map<Long, Integer> counts = new HashMap<>();
                for (TopicVote v : votes) if (v.getSelectedId() != null) counts.merge(v.getSelectedId(), 1, Integer::sum);
                if (qr != null && qr.getOptionVotes() != null)
                    qr.getOptionVotes().forEach((oid, cnt) -> counts.merge(oid, cnt == null ? 0 : cnt, Math::max));
                int leading = 0; String leadingLabel = "";
                List<String> parts = new ArrayList<>();
                for (Map<String, Object> op : parseOptions(topic)) {
                    Long oid = toLong(op.get("id"));
                    int cnt = oid == null ? 0 : counts.getOrDefault(oid, 0);
                    String label = value(String.valueOf(op.get("label")));
                    parts.add(label + " " + cnt + " 票");
                    if (cnt > leading) { leading = cnt; leadingLabel = label; }
                }
                String tally = parts.isEmpty() ? "（无选项）" : String.join("、", parts);
                String resultText = leading >= need ? ("表决通过：" + leadingLabel) : "表决未通过";
                c.decisions.add(di + ". " + title + "：" + tally + "，" + resultText + "。");
                c.resultAppendix.add(cnNum(di) + "、" + title + "【" + mergedTypeLabel(topic) + "】");
                c.resultAppendix.add("　　各选项票数：" + tally + "，" + resultText + "。");
            } else if (hasVotes) {
                anyVote = true;
                int forV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.for_vote).count();
                int agV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.against).count();
                int abV = (int) votes.stream().filter(v -> v.getChoice() == VoteChoice.abstain).count();
                if (qr != null) {
                    if (qr.getForVotes() != null) forV = Math.max(forV, qr.getForVotes());
                    if (qr.getAgVotes() != null) agV = Math.max(agV, qr.getAgVotes());
                    if (qr.getAbVotes() != null) abV = Math.max(abV, qr.getAbVotes());
                }
                String tally = "同意 " + forV + " 票、反对 " + agV + " 票、弃权 " + abV + " 票";
                String resultText = forV >= need ? "表决通过" : "表决未通过";
                c.decisions.add(di + ". " + title + "：" + tally + "，" + resultText + "。");
                // 附页：逐题委员名单（按实际 choice 分组；标签取自枚举，不再硬编码字符串）
                c.resultAppendix.add(cnNum(di) + "、" + title + "【" + mergedTypeLabel(topic) + "】");
                c.resultAppendix.add("　　表决情况：" + tally + "，" + resultText + "。");
                if (votes.isEmpty()) {
                    c.resultAppendix.add("　　（现场汇总表决，未逐人记名）");
                } else {
                    List<String> forNames = new ArrayList<>(), agNames = new ArrayList<>(), abNames = new ArrayList<>();
                    for (TopicVote v : votes) {
                        String nm = v.getUserRole() == null ? "未知委员" : v.getUserRole().getRealName();
                        if (v.getChoice() == VoteChoice.for_vote) forNames.add(nm);
                        else if (v.getChoice() == VoteChoice.against) agNames.add(nm);
                        else if (v.getChoice() == VoteChoice.abstain) abNames.add(nm);
                    }
                    c.resultAppendix.add("　　同意的委员：" + (forNames.isEmpty() ? "（无）" : String.join("、", forNames)) + "。");
                    c.resultAppendix.add("　　反对的委员：" + (agNames.isEmpty() ? "（无）" : String.join("、", agNames)) + "。");
                    if (!abNames.isEmpty())
                        c.resultAppendix.add("　　弃权的委员：" + String.join("、", abNames) + "。");
                }
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
        // 表头逐项一行（0729 用户定）：原先把「议题/时间/主持人…」多项挤一行用空格分隔，
        // 手机窄屏会在字中间断行，排版全乱；改成每项独占一行。唯「应到/实到人数」两项短、并到一行。
        for (String[] row : c.infoRows) {
            boolean sameLine = row.length >= 2 && "应到人数".equals(row[0].replace("  ", ""));
            if (sameLine) {
                List<String> pairs = new ArrayList<>();
                for (int i = 0; i + 1 < row.length; i += 2) pairs.add(row[i].replace("  ", "") + "：" + row[i + 1]);
                sb.append(String.join("　　", pairs)).append('\n');
            } else {
                for (int i = 0; i + 1 < row.length; i += 2)
                    sb.append(row[i].replace("  ", "")).append('：').append(row[i + 1]).append('\n');
            }
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
            boolean orgDone = false;
            for (String raw : minutesText.split("\\R")) {
                String t = raw.strip().replaceAll("^#{1,6}\\s*", ""); // 去掉 AI 稿里的 Markdown 标题记号
                if (t.isEmpty()) { if (bodyStarted) w.gap(6); continue; }
                // 正文开头的标题行不重复（页首已排 docTitle）：含 AI 稿自带的「××业委会会议纪要」
                if (!bodyStarted && (t.equals("会议纪要") || t.equals(meetingTitle) || t.equals(docTitle)
                        || (t.length() <= 30 && t.endsWith("会议纪要")))) continue;
                bodyStarted = true;
                if (isDateLine(t)) { w.right(t); continue; }              // 日期：右对齐
                if (isOrgLine(t)) {                                        // 落款：简化为短名 + 去重（0729 用户定）
                    if (!orgDone) { orgDone = true; w.right(SIGN_ORG); }
                    continue;
                }
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

    /** 会前公告 PDF（0723）：面向全体业主的会议召开公告，公文格式——标题居中 + 段首缩进 + 落款右对齐 + 盖章位。 */
    @Transactional(readOnly = true)
    public PdfFile generateNoticePdf(Long meetingId, String fullText) {
        return generateNoticePdf(meetingId, fullText, "会议公告");
    }

    /** 同一公文排版复用于 会前公告/会后公示（kind 决定导出文件名后缀）。 */
    @Transactional(readOnly = true)
    public PdfFile generateNoticePdf(Long meetingId, String fullText, String kind) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        if (fullText == null || fullText.isBlank())
            throw new IllegalArgumentException("公告内容为空");
        String[] lines = fullText.split("\\R", -1);
        String title = lines.length > 0 ? lines[0].strip() : "会议公告";
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer w = new Writer(doc, loadChineseFont(doc));
            w.title(title);
            w.gap(10);
            for (int i = 1; i < lines.length; i++) {
                String t = lines[i].strip();
                if (t.isEmpty()) { w.gap(6); continue; }
                if (isSignoffLine(t)) { w.right(t); continue; }         // 落款右对齐
                if (t.matches("^[一二三四五六七八九十]+、.*")) { w.line("　　" + t); continue; } // 议程条目
                w.paragraph("　　" + t);
            }
            w.gap(6);
            w.right("（盖章）");
            w.gap(36);
            w.close();
            doc.save(out);
            return new PdfFile(safe(meeting.getTitle()) + "-" + kind + ".pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(kind + "生成失败", e);
        }
    }

    /** 落款行判定：业委会全称（可带届别）或日期行，右对齐排版。 */
    private static boolean isSignoffLine(String t) {
        return t.matches(".{0,30}业主委员会(（第.{1,6}届）)?") || t.matches("\\d{4}年\\d{1,2}月\\d{1,2}日");
    }
    // 会议纪要落款：日期行 / 落款单位行。纪要落款统一简化为短名（不带区划/届别），见 generateMinutesPdf。
    private static final String SIGN_ORG = "阳光花园业主委员会";
    private static boolean isDateLine(String t) { return t.matches("\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日"); }
    private static boolean isOrgLine(String t) { return t.matches(".{0,30}业主委员会(（第.{0,6}届）)?"); }

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
        return com.ywh.util.PdfFontLoader.load(document); // 统一三级查找：显式配置 > 常见路径 > 扫描字体目录
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
