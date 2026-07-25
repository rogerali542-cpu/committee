package com.ywh.service;

import com.ywh.entity.WorkItem;
import com.ywh.repository.WorkItemRepository;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkItemService {
    private final WorkItemRepository repository;

    public List<WorkItem> list(Long communityId, String status) {
        Long currentCommunityId = SecurityUtils.getCurrentCommunityId();
        if (!currentCommunityId.equals(communityId)) {
            throw new IllegalArgumentException("无权访问该小区事项");
        }
        if (status == null || status.isBlank() || "all".equals(status)) {
            return repository.findByCommunityIdOrderByUpdatedAtDesc(communityId);
        }
        return repository.findByCommunityIdAndStatusOrderByUpdatedAtDesc(communityId, status);
    }

    public WorkItem get(Long id) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return repository.findById(id)
                .filter(item -> communityId.equals(item.getCommunityId()))
                .orElseThrow(() -> new IllegalArgumentException("事项不存在"));
    }

    @Transactional
    public WorkItem create(WorkItem item) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        if (item.getCommunityId() != null && !communityId.equals(item.getCommunityId())) {
            throw new IllegalArgumentException("无权为其他小区创建事项");
        }
        item.setCommunityId(communityId);
        if (item.getTitle() == null || item.getTitle().isBlank()) throw new IllegalArgumentException("事项标题不能为空");
        LocalDateTime now = LocalDateTime.now();
        item.setId(null);
        item.setStatus(item.getStatus() == null || item.getStatus().isBlank() ? "todo" : item.getStatus());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return repository.save(item);
    }

    @Transactional
    public WorkItem updateStatus(Long id, Map<String, String> req) {
        WorkItem item = get(id);
        String status = req.get("status");
        if (!List.of("todo", "doing", "done", "closed").contains(status)) {
            throw new IllegalArgumentException("事项状态无效");
        }
        item.setStatus(status);
        item.setUpdatedAt(LocalDateTime.now());
        item.setClosedAt(("done".equals(status) || "closed".equals(status)) ? LocalDateTime.now() : null);
        return repository.save(item);
    }

    @Transactional
    public void remove(Long id) {
        repository.delete(get(id));
    }
}
