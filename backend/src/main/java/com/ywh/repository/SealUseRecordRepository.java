package com.ywh.repository;

import com.ywh.entity.SealUseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SealUseRecordRepository extends JpaRepository<SealUseRecord, Long> {
    /** 按社区取用印台账，最新申请在前。 */
    List<SealUseRecord> findByCommunityIdOrderByCreatedAtDesc(Long communityId);
}
