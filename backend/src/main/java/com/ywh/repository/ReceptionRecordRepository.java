package com.ywh.repository;

import com.ywh.entity.ReceptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ReceptionRecordRepository extends JpaRepository<ReceptionRecord, Long> {
    List<ReceptionRecord> findByCommunityIdOrderByDateDescTimeDesc(Long communityId);

    @Query("SELECT COUNT(r) FROM ReceptionRecord r WHERE r.community.id = :communityId " +
           "AND SUBSTRING(CAST(r.date AS string), 1, 7) = :ym")
    long countByCommunityIdAndMonth(Long communityId, String ym);

    @Query("SELECT COUNT(r) FROM ReceptionRecord r WHERE r.community.id = :communityId " +
           "AND SUBSTRING(CAST(r.date AS string), 1, 4) = :year")
    long countByCommunityIdAndYear(Long communityId, String year);
}
