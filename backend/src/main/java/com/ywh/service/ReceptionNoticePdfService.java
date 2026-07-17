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

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 760;
                textCentered(cs, font, 24, "业主接待日公告", pageW / 2, y);
                y -= 26;
                textCentered(cs, font, 13, org, pageW / 2, y);
                y -= 18;
                line(cs, left, y, pageW - right, y);            // 抬头下的分隔线，公文常规

                y -= 46;
                text(cs, font, 16, "各位业主：", left, y);

                y -= 34;
                // 首行缩进两字 = 32pt（16pt 字），公文正文规矩
                y = paragraph(cs, font, 16, left, y, contentW, 28,
                        "为方便业主反映情况、提出意见建议，" + org + "设立业主接待日，"
                                + "现将接待安排公告如下：", 32);

                y -= 16;
                // 0717 用户定：公告只列时间/地点两项。接待人不上公告——制度里写的是
                // 主任/副主任或委员轮值，具体到人反而每次都要改（页面预览也已同步删掉此行）
                y = item(cs, font, left, y, contentW, "一、接待时间：", value(sys.getTimeDesc()));
                y = item(cs, font, left, y, contentW, "二、接待地点：", value(sys.getPlace()));

                y -= 20;
                y = paragraph(cs, font, 16, left, y, contentW, 28,
                        "欢迎广大业主在上述时间前来反映问题、提出建议。", 32);

                // 落款：右下角，公文规矩。位置固定在页面下方，不跟着正文长度飘——
                // 正文再短也不能让落款吊在半空
                float signY = 210;
                textRight(cs, font, 16, org, pageW - right, signY);
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

    /** 「一、接待时间：xxx」——标签与内容同排，内容过长时在标签宽度处对齐续行 */
    private float item(PDPageContentStream cs, PDFont font, float left, float y,
                       float contentW, String label, String content) throws IOException {
        float size = 16, indent = 32;
        float labelW = font.getStringWidth(label) / 1000f * size;
        text(cs, font, size, label, left + indent, y);
        List<String> lines = wrap(font, size, content, contentW - indent - labelW);
        for (int i = 0; i < lines.size(); i++) {
            text(cs, font, size, lines.get(i), left + indent + labelW, y - i * 26);
        }
        return y - lines.size() * 26 - 6;
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
        List<String> lines = new ArrayList<>();
        lines.add(head);
        lines.addAll(wrap(font, size, rest, contentW));
        return lines;
    }

    /**
     * ⚠ 本方法是全仓库第三份拷贝（另见 AttendanceSheetPdfService / MeetingRecordPdfService）。
     * 路径清单散在三处 = 部署换机器时要改三个地方，已挂清理任务，别再抄第四份。
     */
    private PDFont loadChineseFont(PDDocument document) throws IOException {
        String[] candidates = {
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsunb.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (!f.isFile()) continue;
            if (path.toLowerCase().endsWith(".ttf")) return PDType0Font.load(document, f);
            if (path.toLowerCase().endsWith(".ttc")) {
                try (TrueTypeCollection collection = new TrueTypeCollection(f)) {
                    PDFont[] loaded = new PDFont[1];
                    collection.processAllFonts(ttf -> {
                        if (loaded[0] == null) loaded[0] = PDType0Font.load(document, ttf, true);
                    });
                    if (loaded[0] != null) return loaded[0];
                }
            }
        }
        throw new IllegalStateException("服务器缺少可用的中文字体，无法生成接待日公告");
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
    private String value(String v) { return isBlank(v) ? "未填写" : v.trim(); }
    private boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }

    public record PdfFile(String fileName, byte[] bytes) {}
}
