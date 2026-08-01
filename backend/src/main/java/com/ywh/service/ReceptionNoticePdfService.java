package com.ywh.service;

import com.ywh.entity.Community;
import com.ywh.entity.ReceptionNoticeExport;
import com.ywh.entity.ReceptionSystem;
import com.ywh.repository.ReceptionNoticeExportRepository;
import com.ywh.repository.ReceptionSystemRepository;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 接待日公告 PDF（0717）—— 打印出来贴楼道/发居民的纸质材料。
 *
 * 跟签到表(AttendanceSheetPdfService)是两种东西，排版取向相反：
 *   签到表 = 表单，给委员填，信息密、字号小(9.5~11)、有表格线；
 *   本公告 = 告示，贴在墙上给老人隔一米看，所以字大(正文16、标题24)、行距松、无框线。
 * 别照着签到表的字号抄，那个尺寸贴墙上没人看得清。
 */
@Service
@RequiredArgsConstructor
public class ReceptionNoticePdfService {

    private final ReceptionSystemRepository sysRepo;
    private final ReceptionNoticeExportRepository exportRepo;
    private final ReceptionService receptionService;

    /**
     * 生成并留痕。留痕在这里做而不是 controller：导出成功才算公示过，
     * 生成失败就不该留下「已导出」的假记录，两件事必须同一个事务。
     */
    @Transactional
    public PdfFile generateAndRecord() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        ReceptionSystem sys = sysRepo.findByCommunityId(communityId).orElse(null);
        if (sys == null || isBlank(sys.getTimeDesc())) {
            throw new IllegalArgumentException("请先填写接待时间，再导出公告");
        }
        // 抬头走 ReceptionService 的唯一实现（含库里小区名乱码的兜底），
        // 页面预览读的是同一个，别在这里另起一份判定
        String org = receptionService.noticeOrgName();

        byte[] bytes = draw(org, sys);

        exportRepo.save(ReceptionNoticeExport.builder()
                .community(Community.builder().id(communityId).build())
                .exportedBy(SecurityUtils.getCurrentRealName())
                .exportedAt(LocalDateTime.now())
                .timeDesc(sys.getTimeDesc())
                .place(sys.getPlace())
                .person(sys.getPerson())
                .build());

        return new PdfFile(org + "-业主接待日公告.pdf", bytes);
    }

    private byte[] draw(String org, ReceptionSystem sys) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont font = loadChineseFont(doc);
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float pageW = page.getMediaBox().getWidth();   // A4 = 595pt
            float left = 62, right = 62;
            float contentW = pageW - left - right;

            // 归一化「调整通知」上下文（0731 用户定）。⚠ 判定/句式必须与 ReceptionNotice.vue 逐字一致——
            // 预览就是这张纸：isAdjustment、自适应标题、lead 句、对比框全部照抄前端 computed。
            String reason = isBlank(sys.getAdjustReason()) ? null : sys.getAdjustReason().trim();
            String beforeTime = norm(sys.getPrevTimeDesc());
            String beforePlace = norm(sys.getPrevPlace());
            String afterTime = norm(sys.getTimeDesc());
            String afterPlace = norm(sys.getPlace());
            String effDate = isBlank(sys.getEffectiveDate()) ? null : sys.getEffectiveDate().trim();
            boolean timeChanged = !beforeTime.equals(afterTime);
            boolean placeChanged = !beforePlace.equals(afterPlace);
            boolean adjustment = (!beforeTime.isEmpty() || !beforePlace.isEmpty()) && (timeChanged || placeChanged);
            String person = norm(sys.getPerson());
            boolean rotation = person.isEmpty() || person.contains("轮值");

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 785;
                // 抬头：小区名在上、粗分隔线、自适应大标题（0731 用户定，接待时间调整通知样式）
                textCentered(cs, font, 20, org, pageW / 2, y);
                y -= 16;
                thickLine(cs, left, y, pageW - right, y);
                y -= 48;
                // 一次调整可能同时涉及周期、时间、地点和人员，标题统一使用「安排调整」。
                String title = !adjustment ? "业主接待日公告"
                        : "业主接待安排调整通知";
                textCentered(cs, font, 28, title, pageW / 2, y);

                y -= 52;
                text(cs, font, 16, "敬告各位业主：", left, y);
                y -= 34;

                if (adjustment) {
                    StringBuilder lead = new StringBuilder();
                    if (reason != null) lead.append("因").append(reason).append("，");
                    lead.append("业主接待安排");
                    if (effDate != null) lead.append("自 ").append(effDate).append(" 起");
                    lead.append("调整如下，请留意。");
                    y = paragraph(cs, font, 16, left, y, contentW, 28, lead.toString(), 32);
                    y -= 12;
                    y = drawCompareBox(cs, font, left, y, contentW,
                            beforeTime.isEmpty() ? "未填写" : beforeTime, beforePlace,
                            afterTime.isEmpty() ? "未填写" : afterTime, afterPlace);
                    y -= 14;
                    if (!rotation) {
                        // 值班委员行顶格（不缩进），与「敬告各位业主：」同列，正文段落才缩进
                        y = paragraph(cs, font, 16, left, y, contentW, 28, "值班委员：" + person, 0);
                        y -= 2;
                    }
                    y = paragraph(cs, font, 16, left, y, contentW, 28,
                            "给您带来不便，敬请谅解。欢迎广大业主届时前来反映问题、提出建议。", 32);
                } else {
                    // 平铺公告（首次设置/仅换人）：沿用旧句式
                    y = paragraph(cs, font, 16, left, y, contentW, 28,
                            org + "现将业主接待安排公告如下：", 32);
                    y -= 4;
                    y = paragraph(cs, font, 16, left, y, contentW, 28, "接待时间为：" + value(sys.getTimeDesc()), 32);
                    y = paragraph(cs, font, 16, left, y, contentW, 28, "接待地点为：" + value(sys.getPlace()), 32);
                    y = paragraph(cs, font, 16, left, y, contentW, 28, "接待人员为：" + value(sys.getPerson()), 32);
                    y -= 16;
                    y = paragraph(cs, font, 16, left, y, contentW, 28,
                            "欢迎广大业主届时前来反映问题、提出建议。", 32);
                }

                // 落款：右下角，公文规矩。位置固定在页面下方，不跟着正文长度飘——
                // 正文再短也不能让落款吊在半空
                float signY = 210;
                // 落款统一使用「小区名+业主委员会」，与纪要、公示和页面预览一致
                textRight(cs, font, 16, receptionService.noticeOrgFullName(), pageW - right, signY);
                LocalDate today = LocalDate.now();
                textRight(cs, font, 16,
                        today.getYear() + " 年 " + today.getMonthValue() + " 月 " + today.getDayOfMonth() + " 日",
                        pageW - right, signY - 30);
                // 盖章位：不画框，只留白+提示。画个方框印出来盖歪了更难看
                textRight(cs, font, 12, "（盖章）", pageW - right, signY - 68);
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("接待日公告生成失败", e);
        }
    }

    /**
     * 「原安排 vs 现调整为」对比框（0731 用户定）：外框 + 两行（原安排灰、现调整为黑加大），
     * 每行 label + 值（时间一行、地点另起一行）。返回框底 y。
     */
    private float drawCompareBox(PDPageContentStream cs, PDFont font, float left, float yTop, float contentW,
                                 String beforeTime, String beforePlace, String afterTime, String afterPlace) throws IOException {
        float boxRight = left + contentW;
        float padX = 18, padY = 16, lineH = 25, labelW = 92;
        float valX = left + padX + labelW + 12;
        float valW = boxRight - valX - padX;

        List<String> beforeLines = new ArrayList<>(wrap(font, 15, beforeTime, valW));
        if (!beforePlace.isEmpty()) beforeLines.addAll(wrap(font, 15, beforePlace, valW));
        List<String> afterLines = new ArrayList<>(wrap(font, 17, afterTime, valW));
        if (!afterPlace.isEmpty()) afterLines.addAll(wrap(font, 17, afterPlace, valW));

        float row1H = padY + beforeLines.size() * lineH + padY - 4;
        float row2H = padY + afterLines.size() * lineH + padY - 4;
        float boxH = row1H + row2H;

        // 外框 + 两行间分隔线
        cs.setLineWidth(1.4f);
        cs.addRect(left, yTop - boxH, contentW, boxH);
        cs.stroke();
        line(cs, left, yTop - row1H, boxRight, yTop - row1H);

        // 原安排（灰、15pt）
        float ty = yTop - padY - 13;
        cs.setNonStrokingColor(107, 114, 128);
        text(cs, font, 15, "原安排", left + padX, ty);
        for (int i = 0; i < beforeLines.size(); i++) text(cs, font, 15, beforeLines.get(i), valX, ty - i * lineH);
        // 现调整为（黑、加大 17pt）
        float ty2 = yTop - row1H - padY - 14;
        cs.setNonStrokingColor(31, 41, 55);
        text(cs, font, 15, "现调整为", left + padX, ty2);
        for (int i = 0; i < afterLines.size(); i++) text(cs, font, 17, afterLines.get(i), valX, ty2 - i * lineH);
        cs.setNonStrokingColor(0, 0, 0);   // 复位，后续正文回黑

        return yTop - boxH;
    }

    private float paragraph(PDPageContentStream cs, PDFont font, float size, float left, float y,
                            float contentW, float lineH, String content, float firstIndent) throws IOException {
        List<String> lines = wrapWithFirstIndent(font, size, content, contentW, firstIndent);
        for (int i = 0; i < lines.size(); i++) {
            float x = left + (i == 0 ? firstIndent : 0);
            text(cs, font, size, lines.get(i), x, y - i * lineH);
        }
        return y - lines.size() * lineH;
    }

    /**
     * 按可用宽度断行。逐字量宽而不是按字数切：中英文数字混排时按字数切会长短不一，
     * 「每周日下午 15:00—17:00」这种就会溢出到页边外。
     */
    private List<String> wrap(PDFont font, float size, String s, float maxW) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') { lines.add(cur.toString()); cur.setLength(0); continue; }
            String next = cur.toString() + c;
            if (font.getStringWidth(next) / 1000f * size > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0 || lines.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    private List<String> wrapWithFirstIndent(PDFont font, float size, String s, float contentW,
                                             float firstIndent) throws IOException {
        List<String> first = wrap(font, size, s, contentW - firstIndent);
        if (first.size() <= 1) return first;
        // 首行按缩进宽度断，剩下的按整宽重排，否则每行都少两个字的宽度
        String head = first.get(0);
        String rest = s.substring(head.length());
        if (rest.startsWith("\n")) rest = rest.substring(1);
        List<String> lines = new ArrayList<>();
        lines.add(head);
        lines.addAll(wrap(font, size, rest, contentW));
        return lines;
    }

    /**
     * 字体查找已收敛到 PdfFontLoader（0723）：三份拷贝合一，路径/扫描逻辑只维护一处。
     */
    private PDFont loadChineseFont(PDDocument document) throws IOException {
        return com.ywh.util.PdfFontLoader.load(document);
    }

    private void text(PDPageContentStream cs, PDFont font, float size, String v, float x, float y) throws IOException {
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(v); cs.endText();
    }
    private void textCentered(PDPageContentStream cs, PDFont font, float size, String v, float cx, float y) throws IOException {
        text(cs, font, size, v, cx - font.getStringWidth(v) / 1000f * size / 2, y);
    }
    private void textRight(PDPageContentStream cs, PDFont font, float size, String v, float rightX, float y) throws IOException {
        text(cs, font, size, v, rightX - font.getStringWidth(v) / 1000f * size, y);
    }
    private void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setLineWidth(0.8f); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }
    /** 抬头下的粗分隔线（0731：接待时间调整通知样式，公文抬头下常规粗线） */
    private void thickLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setLineWidth(2.6f); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }
    private String value(String v) { return isBlank(v) ? "未填写" : v.trim(); }
    private boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }
    /** null/空白归一为空串，用于对比框「变没变」判定（与前端一致，null 视作 ''） */
    private String norm(String v) { return v == null ? "" : v.trim(); }

    public record PdfFile(String fileName, byte[] bytes) {}
}
