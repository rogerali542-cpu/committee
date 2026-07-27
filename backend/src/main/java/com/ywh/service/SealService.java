package com.ywh.service;

import com.ywh.entity.Community;
import com.ywh.entity.SealUseRecord;
import com.ywh.enums.SealType;
import com.ywh.enums.SealUseStatus;
import com.ywh.repository.SealUseRecordRepository;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    /** 申请用印：登记印章、用途、关联文件，申请人取当前登录身份。 */
    @Transactional
    public SealUseRecord apply(Map<String, Object> req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        SealType type = SealType.valueOf((String) req.get("sealType"));
        var ur = SecurityUtils.getCurrentUserRole();
        return repo.save(SealUseRecord.builder()
                .community(Community.builder().id(communityId).build())
                .sealType(type)
                .purpose(str(req.get("purpose")))
                .documentName(str(req.get("documentName")))
                .applicantName(ur != null ? ur.getRealName() : null)
                .applicantRole(ur != null && ur.getRole() != null ? ur.getRole().name() : null)
                .status(SealUseStatus.pending)
                .build());
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
        m.put("purpose", r.getPurpose());
        m.put("documentName", r.getDocumentName());
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
}
