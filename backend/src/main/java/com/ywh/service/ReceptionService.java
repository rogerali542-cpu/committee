package com.ywh.service;

import com.ywh.entity.*;
import com.ywh.enums.ReceptionCategory;
import com.ywh.repository.*;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceptionService {

    private final ReceptionSystemRepository sysRepo;
    private final ReceptionRecordRepository recordRepo;
    private final ReceptionEvidenceRepository evRepo;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    public Map<String, Object> getSystem() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        ReceptionSystem sys = sysRepo.findByCommunityId(communityId)
                .orElse(null);
        if (sys == null) return Collections.emptyMap();
        Map<String, Object> result = new HashMap<>();
        result.put("published", sys.getPublished());
        result.put("timeDesc", sys.getTimeDesc());
        result.put("place", sys.getPlace());
        result.put("person", sys.getPerson());
        return result;
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
        if (req.containsKey("published")) sys.setPublished((Boolean) req.get("published"));
        sysRepo.save(sys);
    }

    @Transactional
    public void togglePublished() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        ReceptionSystem sys = sysRepo.findByCommunityId(communityId)
                .orElseThrow(() -> new IllegalArgumentException("接待制度不存在"));
        sys.setPublished(!sys.getPublished());
        sysRepo.save(sys);
    }

    public List<Map<String, Object>> listRecords(String filter) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<ReceptionRecord> records = recordRepo.findByCommunityIdOrderByDateDescTimeDesc(communityId);
        if ("pending".equals(filter)) records = records.stream().filter(r -> !isDone(r)).collect(Collectors.toList());
        if ("done".equals(filter)) records = records.stream().filter(this::isDone).collect(Collectors.toList());
        return records.stream().map(r -> {
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
            m.put("fedProperty", r.getFedProperty());
            m.put("fedOwner", r.getFedOwner());
            m.put("done", isDone(r));
            m.put("needsPropertyFeedback", r.getCategory() == ReceptionCategory.property);
            m.put("propertyStatus", r.getPropertyStatus());
            m.put("propertyStatusLabel", propertyStatusLabel(r.getPropertyStatus()));
            m.put("propertyReply", r.getPropertyReply());
            m.put("propertyRepliedBy", r.getPropertyRepliedBy());
            m.put("propertyRepliedAt", r.getPropertyRepliedAt() != null ? r.getPropertyRepliedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());
    }

    private static final Map<String, String> PROPERTY_STATUS_LABELS = Map.of(
            "pending_dispatch", "待派单",
            "dispatched", "物业未处理",
            "processing", "处理中",
            "replied", "处理完");

    private String propertyStatusLabel(String status) {
        return status != null ? PROPERTY_STATUS_LABELS.getOrDefault(status, "") : "";
    }

    private Map<String, Object> toTaskCard(ReceptionRecord r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("date", r.getDate());
        m.put("time", r.getTime());
        m.put("visitorName", r.getVisitorName());
        m.put("room", r.getRoom());
        m.put("content", r.getContent());
        m.put("propertyStatus", r.getPropertyStatus());
        m.put("propertyStatusLabel", propertyStatusLabel(r.getPropertyStatus()));
        m.put("propertyReply", r.getPropertyReply());
        m.put("propertyRepliedBy", r.getPropertyRepliedBy());
        m.put("propertyRepliedAt", r.getPropertyRepliedAt() != null ? r.getPropertyRepliedAt().toString() : null);
        m.put("evidences", getEvidences(r.getId()));
        return m;
    }

    private static final Set<String> DISPATCHED_STATES = Set.of("dispatched", "processing", "replied");

    /** 物业工作台：派给物业的工单（已派单后均可见）。 */
    public List<Map<String, Object>> listPropertyTasks(String status) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return recordRepo.findByCommunityIdOrderByDateDescTimeDesc(communityId).stream()
                .filter(r -> r.getCategory() == ReceptionCategory.property)
                .filter(r -> r.getPropertyStatus() != null && DISPATCHED_STATES.contains(r.getPropertyStatus()))
                .filter(r -> status == null || "all".equals(status) || status.equals(r.getPropertyStatus()))
                .map(this::toTaskCard)
                .collect(Collectors.toList());
    }

    /** 面向全体业主的物业事项公示看板（只读、脱敏：不含来访业主姓名/房号）。 */
    public List<Map<String, Object>> listPropertyPublic() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return recordRepo.findByCommunityIdOrderByDateDescTimeDesc(communityId).stream()
                .filter(r -> r.getCategory() == ReceptionCategory.property)
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("date", r.getDate());
                    m.put("content", r.getContent());
                    m.put("statusKey", publicStatusKey(r.getPropertyStatus()));
                    m.put("statusLabel", publicStatusLabel(r.getPropertyStatus()));
                    m.put("propertyReply", "replied".equals(r.getPropertyStatus()) ? r.getPropertyReply() : null);
                    m.put("propertyRepliedAt", "replied".equals(r.getPropertyStatus()) && r.getPropertyRepliedAt() != null
                            ? r.getPropertyRepliedAt().toString() : null);
                    return m;
                })
                .collect(Collectors.toList());
    }

    private String publicStatusKey(String s) {
        if ("replied".equals(s)) return "done";
        if ("processing".equals(s)) return "processing";
        return "pending";
    }

    private String publicStatusLabel(String s) {
        if ("replied".equals(s)) return "已处理";
        if ("processing".equals(s)) return "处理中";
        return "待处理";
    }

    /** 物业开始处理：物业未处理 → 处理中。 */
    @Transactional
    public void startProcessing(Long id) {
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        if (!"dispatched".equals(r.getPropertyStatus())) {
            throw new IllegalArgumentException("该工单当前不可开始处理");
        }
        r.setPropertyStatus("processing");
        recordRepo.save(r);
    }

    /** 业委会：把物业类诉求转给物业处理。 */
    @Transactional
    public void dispatchToProperty(Long id) {
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        if (r.getCategory() != ReceptionCategory.property) {
            throw new IllegalArgumentException("仅物业类诉求可转物业处理");
        }
        if ("dispatched".equals(r.getPropertyStatus()) || "replied".equals(r.getPropertyStatus())) {
            throw new IllegalArgumentException("该诉求已转物业");
        }
        r.setPropertyStatus("dispatched");
        recordRepo.save(r);
    }

    /** 物业：回填处理结果。 */
    @Transactional
    public void propertyReply(Long id, String reply) {
        if (reply == null || reply.trim().isEmpty()) {
            throw new IllegalArgumentException("请填写处理结果");
        }
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        if (!"processing".equals(r.getPropertyStatus())) {
            throw new IllegalArgumentException("请先开始处理");
        }
        if (getEvidences(id).isEmpty()) {
            throw new IllegalArgumentException("请先上传处理佐证");
        }
        r.setPropertyStatus("replied");
        r.setPropertyReply(reply.trim());
        r.setPropertyRepliedBy(SecurityUtils.getCurrentRealName());
        r.setPropertyRepliedAt(java.time.LocalDateTime.now());
        r.setFedProperty(true); // 兼容旧字段
        recordRepo.save(r);
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
                .propertyStatus(ReceptionCategory.valueOf((String) req.get("category")) == ReceptionCategory.property
                        ? "pending_dispatch" : null)
                .fedProperty(false)
                .fedOwner(false)
                .build());
    }

    @Transactional
    public void updateResolution(Long id, String resolution) {
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        r.setResolution(resolution);
        recordRepo.save(r);
    }

    @Transactional
    public void toggleFollow(Long id, String field) {
        ReceptionRecord r = recordRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在"));
        if ("fedProperty".equals(field)) r.setFedProperty(!r.getFedProperty());
        else if ("fedOwner".equals(field)) r.setFedOwner(!r.getFedOwner());
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

    private boolean isDone(ReceptionRecord r) {
        if (!r.getFedOwner()) return false;
        // 物业类：物业已回复才算物业侧完成；兼容旧数据的 fedProperty
        return r.getCategory() != ReceptionCategory.property
                || "replied".equals(r.getPropertyStatus())
                || Boolean.TRUE.equals(r.getFedProperty());
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
