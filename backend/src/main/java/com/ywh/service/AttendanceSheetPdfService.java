package com.ywh.service;

import com.ywh.entity.CommitteeMeeting;
import com.ywh.entity.MeetingRecord;
import com.ywh.entity.RecordAttendance;
import com.ywh.enums.MeetingStage;
import com.ywh.repository.CommitteeMeetingRepository;
import com.ywh.repository.MeetingRecordRepository;
import com.ywh.repository.RecordAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceSheetPdfService {
    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingRecordRepository recordRepo;
    private final RecordAttendanceRepository attendanceRepo;

    @Transactional(readOnly = true)
    public PdfFile generate(Long meetingId) {
        CommitteeMeeting meeting = meetingRepo.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议不存在"));
        // 会议开始后即可导出：会后整理（现场结束、stage 仍为 ongoing）正是打印签到表让大家签字的时点
        if (meeting.getStage() == MeetingStage.preparing) {
            throw new IllegalArgumentException("会议开始后才能导出会议签到表");
        }
        MeetingRecord record = recordRepo.findByMeetingId(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("会议记录不存在"));
        List<RecordAttendance> rows = attendanceRepo.findByRecordId(record.getId()).stream()
                .filter(row -> row.getUserRole() != null
                        && row.getUserRole().getRole() != null
                        && !"记录员".equals(row.getUserRole().getRole().name()))
                .toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("本次会议没有参会名单");

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont font = loadChineseFont(document);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                draw(cs, page, font, meeting, rows);
            }
            document.save(out);
            String safeTitle = safeFileName(meeting.getTitle() == null ? "会议" : meeting.getTitle());
            return new PdfFile(safeTitle + "-会议签到表.pdf", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("会议签到表生成失败", e);
        }
    }

    private void draw(PDPageContentStream cs, PDPage page, PDFont font,
                      CommitteeMeeting meeting, List<RecordAttendance> rows) throws IOException {
        float pageW = page.getMediaBox().getWidth();
        float left = 40, right = 40;
        float y = 792;
        textCentered(cs, font, 20, "会议签到表", pageW / 2, y);
        y -= 30;
        String communityName = meeting.getCommunity() == null ? null : meeting.getCommunity().getName();
        String org = isUsableName(communityName) ? communityName.trim() + "业主委员会" : "业主委员会";
        textCentered(cs, font, 11, org, pageW / 2, y);

        y -= 34;
        float labelX = left;
        text(cs, font, 10, "会议名称：" + value(meeting.getTitle()), labelX, y);
        y -= 22;
        text(cs, font, 10, "会议时间：" + value(meeting.getMeetingDate()) + " " + shortTime(meeting), labelX, y);
        text(cs, font, 10, "会议地点：" + value(meeting.getLocation()), 320, y);
        y -= 22;
        int total = rows.size();
        int present = (int) rows.stream().filter(a -> Boolean.TRUE.equals(a.getSignedIn())).count();
        int need = total / 2 + 1;
        text(cs, font, 10, "应到委员：" + total + "人    实到委员：" + present + "人    最低有效出席人数：" + need + "人", labelX, y);
        text(cs, font, 10, present >= need ? "达到有效出席人数" : "未达到有效出席人数", 405, y);

        y -= 28;
        float[] widths = {30, 68, 56, 86, 86, 115, 74};
        String[] headers = {"序号", "姓名", "身份", "会前确认状态", "实际参会状态", "本人签名", "备注"};
        float headerH = 34, rowH = 44;
        drawRow(cs, font, left, y, widths, headerH, headers, true);
        y -= headerH;
        int index = 1;
        for (RecordAttendance a : rows) {
            String confirm = Boolean.TRUE.equals(a.getDeclined()) ? "因故缺席"
                    : Boolean.TRUE.equals(a.getSignedIn()) ? "已确认参会" : "未确认";
            String actual = Boolean.TRUE.equals(a.getSignedIn())
                    ? ("remote".equals(a.getAttendanceMode()) ? "已参会（远程）" : "已参会") : "未参会";
            String role = roleLabel(a.getUserRole().getRole().name());
            drawRow(cs, font, left, y, widths, rowH,
                    new String[]{String.valueOf(index++), a.getUserRole().getRealName(), role, confirm, actual, "", ""}, false);
            y -= rowH;
        }

        y -= 36;
        text(cs, font, 11, "主持人签字：________________________", left, y);
        y -= 34;
        text(cs, font, 11, "日期：________年____月____日", left, y);
        text(cs, font, 11, "业主委员会盖章：", 330, y);
        y -= 54;
        line(cs, 330, y, pageW - right, y);
    }

    private void drawRow(PDPageContentStream cs, PDFont font, float x, float top, float[] widths,
                         float height, String[] values, boolean header) throws IOException {
        float totalW = 0; for (float w : widths) totalW += w;
        cs.setLineWidth(header ? 0.8f : 0.5f);
        line(cs, x, top, x + totalW, top);
        line(cs, x, top - height, x + totalW, top - height);
        float cursor = x;
        line(cs, cursor, top, cursor, top - height);
        for (int i = 0; i < widths.length; i++) {
            float center = cursor + widths[i] / 2;
            textCentered(cs, font, header ? 9.5f : 10, values[i] == null ? "" : values[i], center,
                    top - height / 2 - 3.5f);
            cursor += widths[i];
            line(cs, cursor, top, cursor, top - height);
        }
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        return com.ywh.util.PdfFontLoader.load(document); // 统一三级查找：显式配置 > 常见路径 > 扫描字体目录
    }

    private void text(PDPageContentStream cs, PDFont font, float size, String value, float x, float y) throws IOException {
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(value); cs.endText();
    }
    private void textCentered(PDPageContentStream cs, PDFont font, float size, String value, float centerX, float y) throws IOException {
        float width = font.getStringWidth(value) / 1000f * size;
        text(cs, font, size, value, centerX - width / 2, y);
    }
    private void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }
    private String shortTime(CommitteeMeeting m) {
        return m.getMeetingTime() == null ? "" : m.getMeetingTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }
    private String roleLabel(String role) {
        return "主任".equals(role) || "副主任".equals(role) ? role : "委员";
    }
    private String value(Object value) { return value == null ? "未记录" : String.valueOf(value); }
    private boolean isUsableName(String value) {
        return value != null && !value.isBlank() && !value.matches("[?？\\s]+") && !value.contains("�");
    }
    private String safeFileName(String s) { return s.replaceAll("[\\\\/:*?\"<>|]", "_"); }

    public record PdfFile(String fileName, byte[] bytes) {}
}
