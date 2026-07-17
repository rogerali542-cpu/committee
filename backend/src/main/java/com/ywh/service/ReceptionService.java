package com.ywh.service;

import com.ywh.entity.*;
import com.ywh.enums.ReceptionCategory;
import com.ywh.repository.*;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 接待。0716 重做（用户定方案 A）：内部派单流（propertyStatus 状态机 + 物业侧工作台）整体下线，
 * 改为把诉求派发到外部工单系统（见 ReceptionTicketService），物业以后只在那边干活。
 *
 * 办结口径同时改了：原先 done 只认 fedOwner（由「向业主反馈」那个布尔开关写），
 * 内部派单一删、那个开关也没了，done 就没有任何输入源，每条接待会永远挂在待跟进里。
 * 现在 done = 填了处理结果。语义上也更顺：派工单 ≠ 办结 —— 工单只是派出去，物业还没修完，
 * 这件事仍挂在委员名下；等他知道结果、填上处理结果，才算闭环。
 */
@Service
@RequiredArgsConstructor
public class ReceptionService {

    private final ReceptionSystemRepository sysRepo;
    private final ReceptionRecordRepository recordRepo;
    private final ReceptionEvidenceRepository evRepo;
    private final ReceptionNoticeExportRepository noticeExportRepo;
    private final CommunityRepository communityRepo;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    public Map<String, Object> getSystem() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        ReceptionSystem sys = sysRepo.findByCommunityId(communityId)
                .orElse(null);
        Map<String, Object> result = new HashMap<>();
        // 公告抬头（已算好的整串，不是原始小区名）。给整串而不是让前端自己拼，理由见 noticeOrgName()。
        result.put("orgName", noticeOrgName());
        // sys 为空（从没设过接待安排）也要把抬头带回去，否则新社区第一次进页面预览是空的
        if (sys == null) return result;
        result.put("published", sys.getPublished());
        result.put("timeDesc", sys.getTimeDesc());
        result.put("place", sys.getPlace());
        result.put("person", sys.getPerson());
        result.put("adjustReason", sys.getAdjustReason());
        result.put("updatedAt", sys.getUpdatedAt() != null ? sys.getUpdatedAt().toString() : null);
        return result;
    }

    /**
     * 接待日公告的抬头，例如「阳光家园业主委员会」。
     *
     * 这里是全仓库该规则的唯一实现，页面预览(getSystem)和 PDF(ReceptionNoticePdfService) 都调它。
     * 各判各的会出事：本地库里 community.name 就是坏的（存着 4 个 '?'，是早年 latin1 连接
     * 写中文写坏的，MinutesView.vue:70 那条注释说的也是它）。PDF 侧有 isUsableName 挡掉、
     * 退回「业主委员会」；前端若只判非空，预览就会显示「????业主委员会」——
     * 预览跟印出来的纸不一样，预览就白做了。所以只留一份判定。
     *
     * ⚠ 乱码是数据问题不是代码问题：这里只是兜住不让它印到纸上，
     * 修好 community.name 之后抬头会自动带上小区名，不用改代码。
     */
    public String noticeOrgName() {
        String name = communityRepo.findById(SecurityUtils.getCurrentCommunityId())
                .map(Community::getName).orElse(null);
        boolean usable = name != null && !name.isBlank()
                && !name.matches("[?？\\s]+") && !name.contains("�");
        return usable ? name.trim() + "业主委员会" : "业主委员会";
    }

    /** 接待日公告的导出留痕（快照，不是当前设置——见 ReceptionNoticeExport 的注释）。 */
    public List<Map<String, Object>> listNoticeExports() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return noticeExportRepo.findByCommunityIdOrderByExportedAtDesc(communityId).stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("exportedBy", e.getExportedBy());
            m.put("exportedAt", e.getExportedAt() != null ? e.getExportedAt().toString() : null);
            m.put("timeDesc", e.getTimeDesc());
            m.put("place", e.getPlace());
            m.put("person", e.getPerson());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void updateSystem(Map<String, Object> req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        ReceptionSystem sys = sysRepo.findByCommunityId(communityId)
                .orElse(ReceptionSystem.builder()
                        .community(Community.builder().id(communityId).build())
                        .published(true)
                        .build());
        if (req.containsKey("timeDesc")) sys.setTimeDesc((String) req.get("timeDesc"));
        if (req.containsKey("place")) sys.setPlace((String) req.get("place"));
        if (req.containsKey("person")) sys.setPerson((String) req.get("person"));
        if (req.containsKey("adjustReason")) sys.setAdjustReason((String) req.get("adjustReason"));
        if (req.containsKey("published")) sys.setPublished((Boolean) req.get("published"));
        sys.setUpdatedAt(LocalDateTime.now());
        sysRepo.save(sys);
    }

    public List<Map<String, Object>> listRecords(String filter) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<ReceptionRecord> records = recordRepo.findByCommunityIdOrderByDateDescTimeDesc(communityId);
        if ("pending".equals(filter)) records = records.stream().filter(r -> !isDone(r)).collect(Collectors.toList());
        if ("done".equals(filter)) records = records.stream().filter(this::isDone).collect(Collectors.toList());
        return records.stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 单条详情：新的「单条处理页」进来就拉这个。 */
    public Map<String, Object> getRecord(Long id) {
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        return toVO(r);
    }

    private Map<String, Object> toVO(ReceptionRecord r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("date", r.getDate());
        m.put("time", r.getTime());
        m.put("visitorName", r.getVisitorName());
        m.put("room", r.getRoom());
        m.put("receiver", r.getReceiver());
        m.put("category", r.getCategory().name());
        m.put("categoryLabel", r.getCategory().getLabel());
        m.put("content", r.getContent());
        m.put("resolution", r.getResolution());
        m.put("done", isDone(r));
        // 外部工单
        m.put("ticketNo", r.getTicketNo());
        m.put("ticketPushed", r.getTicketPushedAt() != null);
        m.put("ticketPushedAt", r.getTicketPushedAt() != null ? r.getTicketPushedAt().toString() : null);
        // 转物业（另一条路，见 setPropertyTransferred）
        m.put("propertyTransferred", r.getPropertyTransferredAt() != null);
        m.put("propertyTransferredAt", r.getPropertyTransferredAt() != null ? r.getPropertyTransferredAt().toString() : null);
        // 佐证：原先只有 toTaskCard（物业侧）放了这个键，listRecords 从没放过，
        // 于是接待页佐证数永远显示 0、永远走空状态，哪怕上传成功已落库。0716 修。
        m.put("evidences", getEvidences(r.getId()));
        return m;
    }

    @Transactional
    public ReceptionRecord create(Map<String, Object> req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return recordRepo.save(ReceptionRecord.builder()
                .community(Community.builder().id(communityId).build())
                .date(parseDate((String) req.get("date")))
                .time(parseTime((String) req.get("time")))
                .visitorName((String) req.get("visitorName"))
                .room((String) req.get("room"))
                .receiver((String) req.get("receiver"))
                .category(ReceptionCategory.valueOf((String) req.get("category")))
                .content((String) req.get("content"))
                .resolution("")
                .build());
    }

    /** 填写处理结果 —— 这就是办结动作（isDone 以它为准）。 */
    @Transactional
    public void updateResolution(Long id, String resolution) {
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        r.setResolution(resolution != null ? resolution.trim() : "");
        recordRepo.save(r);
    }

    /**
     * 转物业 —— 只在本系统打个标记，不发任何外部请求（0717 用户定：「开关（假按钮）」）。
     * 跟 ReceptionTicketService.push 是两条不同的路：那条真的 POST 到外部工单系统、
     * 拿得到对方单号、派出去就撤不回；这条纯粹是委员自己联系了物业、在这记一笔，所以可反悔。
     * ⚠ 故意不参与 isDone —— 转出去 ≠ 办结，事情仍挂在委员名下，填了处理结果才闭环。
     */
    @Transactional
    public void setPropertyTransferred(Long id, boolean transferred) {
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        r.setPropertyTransferredAt(transferred ? LocalDateTime.now() : null);
        recordRepo.save(r);
    }

    @Transactional
    public void remove(Long id) {
        recordRepo.deleteById(id);
    }

    public Map<String, Object> getStats() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<ReceptionRecord> records = recordRepo.findByCommunityIdOrderByDateDescTimeDesc(communityId);
        String ym = TODAY.getYear() + "-" + String.format("%02d", TODAY.getMonthValue());
        long monthCount = records.stream().filter(r -> r.getDate().toString().startsWith(ym)).count();
        long yearCount = records.stream().filter(r -> r.getDate().getYear() == TODAY.getYear()).count();
        long pending = records.stream().filter(r -> !isDone(r)).count();
        long done = records.stream().filter(this::isDone).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("monthCount", monthCount);
        stats.put("yearCount", yearCount);
        stats.put("pending", pending);
        stats.put("done", done);
        stats.put("total", records.size());
        return stats;
    }

    /**
     * 办结 = 填了处理结果。0716 重写（原口径 done = fedOwner && (非物业 || propertyStatus=='replied' || fedProperty)）。
     * ⚠ 全仓库另有一份 DashboardService.isReceptionDone，必须与此保持同一口径，否则首页和工作台会对不上。
     */
    public boolean isDone(ReceptionRecord r) {
        return r.getResolution() != null && !r.getResolution().trim().isEmpty();
    }

    // ── 佐证 ──

    public List<Map<String, Object>> getEvidences(Long recordId) {
        return evRepo.findByRecordId(recordId).stream().map(ev -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ev.getId());
            m.put("fileName", ev.getFileName());
            m.put("fileType", ev.getFileType());
            m.put("fileUrl", ev.getFileUrl());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> addEvidence(Long recordId, String fileName, String fileType, String fileUrl) {
        ReceptionEvidence ev = ReceptionEvidence.builder()
                .recordId(recordId).fileName(fileName).fileType(fileType).fileUrl(fileUrl).build();
        ev = evRepo.save(ev);
        Map<String, Object> m = new HashMap<>();
        m.put("id", ev.getId());
        m.put("fileName", ev.getFileName());
        m.put("fileType", ev.getFileType());
        m.put("fileUrl", ev.getFileUrl());
        return m;
    }

    @Transactional
    public void removeEvidence(Long recordId, Long evId) {
        evRepo.deleteById(evId);
    }

    private LocalDate parseDate(String s) { return s != null ? LocalDate.parse(s) : null; }
    private LocalTime parseTime(String s) { return s != null ? LocalTime.parse(s) : null; }
}
