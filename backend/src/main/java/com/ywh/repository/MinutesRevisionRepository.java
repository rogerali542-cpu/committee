package com.ywh.repository;

import com.ywh.entity.MinutesRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MinutesRevisionRepository extends JpaRepository<MinutesRevision, Long> {
    List<MinutesRevision> findByMeetingIdOrderByVersionNoDesc(Long meetingId);
    Optional<MinutesRevision> findFirstByMeetingIdOrderByVersionNoDesc(Long meetingId);
}
