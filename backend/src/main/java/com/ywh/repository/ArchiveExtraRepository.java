package com.ywh.repository;

import com.ywh.entity.ArchiveExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArchiveExtraRepository extends JpaRepository<ArchiveExtra, Long> {
    List<ArchiveExtra> findByMeetingId(Long meetingId);
}
