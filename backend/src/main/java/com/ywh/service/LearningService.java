package com.ywh.service;

import com.ywh.entity.*;
import com.ywh.enums.LearningCategory;
import com.ywh.enums.LearningType;
import com.ywh.enums.MeetingStage;
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

@Service
@RequiredArgsConstructor
public class LearningService {

    private final LearningRecordRepository repo;
    private final LearningEvidenceRepository evRepo;
    private final LearningSignInRepository signInRepo;

    public List<Map<String, Object>> list(String type, String stage) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        MeetingStage lStage = stage != null ? MeetingStage.valueOf(stage) : null;

        // "training" = street + special 组合筛选
        boolean isTraining = "training".equals(type);
        List<LearningType> types = isTraining
                ? List.of(LearningType.street, LearningType.special)
                : List.of(type != null ? LearningType.valueOf(type) : LearningType.internal);

        List<LearningRecord> records;
        if (lStage != null) {
            records = isTraining
                    ? repo.findByCommunityIdAndTypeInAndStageOrderByDateDesc(communityId, types, lStage)
                    : repo.findByCommunityIdAndTypeAndStageOrderByDateDesc(communityId, types.get(0), lStage);
        } else {
            records = isTraining
                    ? repo.findByCommunityIdAndTypeInOrderByDateDesc(communityId, types)
                    : repo.findByCommunityIdAndTypeOrderByDateDesc(communityId, types.get(0));
        }

        return records.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("date", r.getDate());
            m.put("time", r.getTime());
            m.put("location", r.getLocation());
            m.put("trainer", r.getTrainer());
            m.put("description", r.getDescription());
            m.put("type", r.getType().name());
            m.put("category", r.getCategory() != null ? r.getCategory().name() : "internal");
            m.put("categoryLabel", r.getCategory() != null ? r.getCategory().getLabel() : "内部学习");
            m.put("stage", r.getStage().name());
            m.put("progress", r.getProgress());
            m.put("attendees", r.getAttendees());
            m.put("notified", r.getNotified());
            // 佐证
            List<LearningEvidence> evs = evRepo.findByRecordId(r.getId());
            m.put("evidences", evs.stream().map(ev -> {
                Map<String, Object> em = new HashMap<>();
                em.put("id", ev.getId());
                em.put("fileName", ev.getFileName());
                em.put("fileType", ev.getFileType());
                em.put("fileUrl", ev.getFileUrl());
                return em;
            }).collect(Collectors.toList()));
            // 签到
            List<LearningSignIn> signs = signInRepo.findByRecordId(r.getId());
            Map<String, Boolean> signIns = new LinkedHashMap<>();
            for (LearningSignIn s : signs) signIns.put(s.getRealName(), s.getSignedIn());
            m.put("signIns", signIns);
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getCounts(String type) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        LearningType lType = type != null ? LearningType.valueOf(type) : LearningType.internal;
        List<LearningRecord> records = repo.findByCommunityIdAndTypeOrderByDateDesc(communityId, lType);
        Map<String, Object> counts = new HashMap<>();
        counts.put("pending", records.stream().filter(r -> r.getStage() == MeetingStage.preparing).count());
        counts.put("ongoing", records.stream().filter(r -> r.getStage() == MeetingStage.ongoing).count());
        counts.put("ended", records.stream().filter(r -> r.getStage() == MeetingStage.ended).count());
        return counts;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        String typeStr = (String) req.getOrDefault("type", "internal");
        LearningType lType = LearningType.internal;
        try { lType = LearningType.valueOf(typeStr); } catch (Exception ignored) {}

        // 分类以显式 category 为准；旧客户端未传时按 type 推断（internal→内部，其余→外部）
        String catStr = (String) req.get("category");
        LearningCategory lCategory;
        if (catStr != null) {
            try { lCategory = LearningCategory.valueOf(catStr); }
            catch (Exception e) { lCategory = LearningCategory.internal; }
        } else {
            lCategory = lType == LearningType.internal ? LearningCategory.internal : LearningCategory.external;
        }

        LearningRecord r = LearningRecord.builder()
                .community(Community.builder().id(communityId).build())
                .title((String) req.getOrDefault("title", "新建学习"))
                .date(req.get("date") != null ? LocalDate.parse(req.get("date").toString()) : LocalDate.now())
                .time(req.get("time") != null ? LocalTime.parse(req.get("time").toString()) : LocalTime.of(14, 0))
                .location((String) req.getOrDefault("location", ""))
                .trainer((String) req.getOrDefault("trainer", ""))
                .description((String) req.getOrDefault("description", ""))
                .attendees((String) req.getOrDefault("attendees", ""))
                .type(lType)
                .category(lCategory)
                .stage(MeetingStage.preparing)
                .progress(0)
                .notified(false)
                .build();
        r = repo.save(r);

        // 根据参训人员生成签到表
        String attendees = (String) req.getOrDefault("attendees", "");
        if (attendees != null && !attendees.isEmpty()) {
            for (String name : attendees.split("[,，、\\s]+")) {
                String n = name.trim();
                if (!n.isEmpty()) {
                    LearningSignIn si = LearningSignIn.builder()
                            .recordId(r.getId()).realName(n).signedIn(false).build();
                    signInRepo.save(si);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", r.getId());
        return result;
    }

    @Transactional
    public void remove(Long id) {
        LearningRecord record = requireCurrentCommunityRecord(id);
        repo.delete(record);
    }

    @Transactional
    public void startLearning(Long id) {
        LearningRecord r = requireCurrentCommunityRecord(id);
        r.setStage(MeetingStage.ongoing);
        r.setProgress(10);
        // 重置签到状态
        List<LearningSignIn> signs = signInRepo.findByRecordId(id);
        for (LearningSignIn s : signs) { s.setSignedIn(false); s.setSignedAt(null); }
        signInRepo.saveAll(signs);
        repo.save(r);
    }

    @Transactional
    public void finishLearning(Long id) {
        LearningRecord r = requireCurrentCommunityRecord(id);
        r.setStage(MeetingStage.ended);
        r.setProgress(100);
        repo.save(r);
    }

    // 修改分类（内部学习 / 外部培训）：详情页可改，用于纠正误分类
    @Transactional
    public void setCategory(Long id, String category) {
        LearningRecord r = requireCurrentCommunityRecord(id);
        try { r.setCategory(LearningCategory.valueOf(category)); }
        catch (Exception e) { throw new IllegalArgumentException("分类无效"); }
        repo.save(r);
    }

    // 通知全员；names 非空且处于准备阶段时，把通知页选定的参加人员落库（更新 attendees + 重建签到名单）
    @Transactional
    public void notifyAll(Long id, List<String> names) {
        LearningRecord r = requireCurrentCommunityRecord(id);
        if (names != null && !names.isEmpty() && r.getStage() == MeetingStage.preparing) {
            Set<String> clean = names.stream()
                    .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!clean.isEmpty()) {
                r.setAttendees(String.join("、", clean));
                signInRepo.deleteAll(signInRepo.findByRecordId(id));
                for (String n : clean) {
                    signInRepo.save(LearningSignIn.builder().recordId(id).realName(n).signedIn(false).build());
                }
            }
        }
        r.setNotified(true);
        repo.save(r);
    }

    // 签到
    @Transactional
    public void signIn(Long id) {
        requireCurrentCommunityRecord(id);
        UserRoleEntity currentUser = SecurityUtils.getCurrentUserRole();
        String realName = currentUser.getRealName();
        LearningSignIn si = signInRepo.findByRecordIdAndRealName(id, realName).orElse(null);
        if (si != null) {
            si.setSignedIn(!si.getSignedIn());
            si.setSignedAt(si.getSignedIn() ? LocalDateTime.now() : null);
            signInRepo.save(si);
        }
    }

    // 培训结束后由负责人登记实际参加人员，不要求委员在软件内现场签到
    @Transactional
    public void setAttendance(Long id, List<String> attendedNames) {
        requireCurrentCommunityRecord(id);
        Set<String> attended = attendedNames == null
                ? Collections.emptySet()
                : attendedNames.stream().filter(Objects::nonNull).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        List<LearningSignIn> signs = signInRepo.findByRecordId(id);
        for (LearningSignIn s : signs) {
            boolean present = attended.contains(s.getRealName());
            s.setSignedIn(present);
            s.setSignedAt(present ? LocalDateTime.now() : null);
        }
        signInRepo.saveAll(signs);
    }

    // 佐证
    @Transactional
    public Map<String, Object> addEvidence(Long id, String fileName, String fileType, String fileUrl) {
        requireCurrentCommunityRecord(id);
        LearningEvidence ev = LearningEvidence.builder()
                .recordId(id).fileName(fileName).fileType(fileType).fileUrl(fileUrl).build();
        ev = evRepo.save(ev);
        Map<String, Object> m = new HashMap<>();
        m.put("id", ev.getId());
        m.put("fileName", ev.getFileName());
        m.put("fileType", ev.getFileType());
        m.put("fileUrl", ev.getFileUrl());
        return m;
    }

    @Transactional
    public void removeEvidence(Long id, Long evId) {
        requireCurrentCommunityRecord(id);
        LearningEvidence evidence = evRepo.findById(evId)
                .filter(ev -> id.equals(ev.getRecordId()))
                .orElseThrow(() -> new IllegalArgumentException("学习材料不存在"));
        evRepo.delete(evidence);
    }

    private LearningRecord requireCurrentCommunityRecord(Long id) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return repo.findById(id)
                .filter(record -> record.getCommunity() != null
                        && communityId.equals(record.getCommunity().getId()))
                // 对跨小区请求统一返回“不存在”，避免泄露其他小区是否有该编号。
                .orElseThrow(() -> new IllegalArgumentException("学习记录不存在"));
    }
}
