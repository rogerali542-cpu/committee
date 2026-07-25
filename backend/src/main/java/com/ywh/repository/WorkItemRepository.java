package com.ywh.repository;

import com.ywh.entity.WorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkItemRepository extends JpaRepository<WorkItem, Long> {
    List<WorkItem> findByCommunityIdOrderByUpdatedAtDesc(Long communityId);
    List<WorkItem> findByCommunityIdAndStatusOrderByUpdatedAtDesc(Long communityId, String status);
}
