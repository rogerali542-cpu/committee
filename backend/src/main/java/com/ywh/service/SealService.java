package com.ywh.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywh.entity.Community;
import com.ywh.entity.SealUseRecord;
import com.ywh.enums.SealType;
import com.ywh.enums.SealUseStatus;
import com.ywh.repository.SealUseRecordRepository;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 印章管理：用印申请 + 使用记录留档（用印台账）。
 * 依据《印章管理制度》：印章三枚由主任、副主任分人保管；用印须登记时间、用途、文件、申请人，
 * 并经保管人（主任/副主任）确认。审批口径与角色控制在 SealController 的 @RequireRole。
 */
@Service
@RequiredArgsConstructor
public class SealService {

    private final SealUseRecordRepository repo;
    private final ObjectMapper objectMapper;

    /** 印章清单（固定三枚），供申请表选择与页面展示。 */
    public List<Map<String, Object>> listSeals() {
        List<Map<String, Object>> seals = new ArrayList<>();
        for (SealType t : SealType.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", t.name());
            m.put("label", t.getLabel());
            seals.add(m);
        }
        return seals;
    }

    /** 用印台账。filter = all | pending（待确认） | approved（已用印）。 */
    public List<Map<String, Object>> listRecords(String filter) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<SealUseRecord> records = repo.findByCommunityIdOrderByCreatedAtDesc(communityId);
        if ("pending".equals(filter)) {
            records = records.stream().filter(r -> r.getStatus() == SealUseStatus.pending).collect(Collectors.toList());
        } else if ("approved".equals(filter)) {
            records = records.stream().filter(r -> r.getStatus() == SealUseStatus.approved).collect(Collectors.toList());
        }
        return records.stream().map(this::toVO).collect(Collectors.toList());
    }

    public Map<String, Object> getStats() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        List<SealUseRecord> records = repo.findByCommunityIdOrderByCreatedAtDesc(communityId);
        Map<String, Object> m = new HashMap<>();
        m.put("total", records.size());
        m.put("pending", records.stream().filter(r -> r.getStatus() == SealUseStatus.pending).count());
        m.put("approved", records.stream().filter(r -> r.getStatus() == SealUseStatus.approved).count());
        return m;
    }

    /** 申请用印：登记印章、用途、附件（0728 起文件信息写进用途+附件，不再单独填文件名），申请人取当前登录身份。 */
    @Transactional
    public SealUseRecord apply(Map<String, Object> req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        SealType type = SealType.valueOf((String) req.get("sealType"));
        var ur = SecurityUtils.getCurrentUserRole();
        LocalDate useDate = parseDate(req.get("useDate"));
        if (useDate != null && useDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("用印时间不能早于今天");
        }
        return repo.save(SealUseRecord.builder()
                .community(Community.builder().id(communityId).build())
                .sealType(type)
                .useDate(useDate)
                .purpose(str(req.get("purpose")))
                .attachments(serializeAttachments(req.get("attachments")))
                .applicantName(ur != null ? ur.getRealName() : null)
                .applicantRole(ur != null && ur.getRole() != null ? ur.getRole().name() : null)
                .status(SealUseStatus.pending)
                .build());
    }

    /** 附件入库：只留 url/name/type/size 四个字段，最多 9 件，序列化为 JSON 存 TEXT 列。 */
    private String serializeAttachments(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) return null;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String url = str(m.get("url"));
            if (url == null) continue;
            Map<String, Object> a = new HashMap<>();
            a.put("url", url);
            a.put("name", str(m.get("name")));
            a.put("type", str(m.get("type")));
            a.put("size", m.get("size"));
            out.add(a);
            if (out.size() >= 9) break;
        }
        if (out.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(out); } catch (Exception e) { return null; }
    }

    private List<Map<String, Object>> parseAttachments(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { return Collections.emptyList(); }
    }

    /** 保管人确认用印（盖章）：状态转已用印，记确认人与时间——即台账留档时间。 */
    @Transactional
    public void confirm(Long id) {
        SealUseRecord r = requireRecord(id);
        r.setStatus(SealUseStatus.approved);
        r.setCustodianName(SecurityUtils.getCurrentRealName());
        r.setConfirmedAt(LocalDateTime.now());
        r.setRejectReason(null);
        repo.save(r);
    }

    /** 保管人驳回：状态转已驳回，记驳回人、时间与理由。 */
    @Transactional
    public void reject(Long id, String reason) {
        SealUseRecord r = requireRecord(id);
        r.setStatus(SealUseStatus.rejected);
        r.setCustodianName(SecurityUtils.getCurrentRealName());
        r.setConfirmedAt(LocalDateTime.now());
        r.setRejectReason(reason != null ? reason.trim() : null);
        repo.save(r);
    }

    @Transactional
    public void remove(Long id) {
        repo.delete(requireRecord(id));
    }

    private SealUseRecord requireRecord(Long id) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        SealUseRecord r = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("用印记录不存在"));
        if (communityId == null || !communityId.equals(r.getCommunity().getId())) {
            throw new IllegalArgumentException("无权操作该用印记录");
        }
        return r;
    }

    private Map<String, Object> toVO(SealUseRecord r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("sealType", r.getSealType().name());
        m.put("sealLabel", r.getSealType().getLabel());
        m.put("useDate", r.getUseDate() != null ? r.getUseDate().toString() : null);
        m.put("purpose", r.getPurpose());
        m.put("documentName", r.getDocumentName());
        m.put("attachments", parseAttachments(r.getAttachments()));
        m.put("applicantName", r.getApplicantName());
        m.put("applicantRole", r.getApplicantRole());
        m.put("status", r.getStatus().name());
        m.put("statusLabel", r.getStatus().getLabel());
        m.put("custodianName", r.getCustodianName());
        m.put("confirmedAt", r.getConfirmedAt() != null ? r.getConfirmedAt().toString() : null);
        m.put("rejectReason", r.getRejectReason());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return m;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /** 解析前端传来的日期字符串（yyyy-MM-dd）；空或非法返回 null。 */
    private static LocalDate parseDate(Object o) {
        String s = str(o);
        if (s == null) return null;
        try { return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s); }
        catch (Exception e) { return null; }
    }
}
