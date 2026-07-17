package com.ywh.repository;

import com.ywh.entity.ReceptionNoticeExport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReceptionNoticeExportRepository extends JpaRepository<ReceptionNoticeExport, Long> {
    List<ReceptionNoticeExport> findByCommunityIdOrderByExportedAtDesc(Long communityId);
}
