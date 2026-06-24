package com.ywh.controller;

import com.ywh.entity.MeetingDelivery;
import com.ywh.entity.UserRoleEntity;
import com.ywh.repository.MeetingDeliveryRepository;
import com.ywh.util.Result;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final MeetingDeliveryRepository deliveryRepo;

    @GetMapping
    public Result<Map<String, Object>> list() {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur == null) {
            throw new IllegalArgumentException("未登录");
        }

        List<Map<String, Object>> list = deliveryRepo.findByUserRoleIdOrderByIdDesc(ur.getId()).stream()
                .filter(d -> Boolean.TRUE.equals(d.getNoticeDelivered()) || Boolean.TRUE.equals(d.getMaterialDelivered()))
                .map(this::toNotification)
                .toList();
        long unread = list.stream().filter(n -> !Boolean.TRUE.equals(n.get("read"))).count();

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("unread", unread);
        return Result.ok(data);
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        MeetingDelivery d = deliveryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (ur == null || !d.getUserRole().getId().equals(ur.getId())) {
            throw new IllegalArgumentException("通知不存在");
        }
        markRead(d);
        deliveryRepo.save(d);
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Void> readAll() {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur == null) {
            throw new IllegalArgumentException("未登录");
        }
        List<MeetingDelivery> deliveries = deliveryRepo.findByUserRoleIdOrderByIdDesc(ur.getId());
        deliveries.forEach(this::markRead);
        deliveryRepo.saveAll(deliveries);
        return Result.ok();
    }

    private Map<String, Object> toNotification(MeetingDelivery d) {
        boolean hasMaterial = Boolean.TRUE.equals(d.getMaterialDelivered());
        boolean read = d.getNoticeReadAt() != null && (!hasMaterial || d.getMaterialReadAt() != null);
        Map<String, Object> n = new HashMap<>();
        n.put("id", d.getId());
        n.put("meetingId", d.getMeeting().getId());
        n.put("type", "notice");
        n.put("title", hasMaterial ? "业委会会议通知与材料" : "业委会会议通知");
        n.put("content", d.getMeeting().getTitle());
        n.put("read", read);
        n.put("createdAt", d.getMeeting().getUpdatedAt() != null
                ? d.getMeeting().getUpdatedAt().toString()
                : d.getMeeting().getCreatedAt().toString());
        return n;
    }

    private void markRead(MeetingDelivery d) {
        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(d.getNoticeDelivered()) && d.getNoticeReadAt() == null) {
            d.setNoticeReadAt(now);
        }
        if (Boolean.TRUE.equals(d.getMaterialDelivered()) && d.getMaterialReadAt() == null) {
            d.setMaterialReadAt(now);
        }
    }
}
