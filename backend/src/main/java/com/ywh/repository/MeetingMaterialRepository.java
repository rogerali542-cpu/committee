package com.ywh.repository;

import com.ywh.entity.MeetingMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MeetingMaterialRepository extends JpaRepository<MeetingMaterial, Long> {
    List<MeetingMaterial> findByMeetingId(Long meetingId);
    void deleteByMeetingIdAndId(Long meetingId, Long id);
}
