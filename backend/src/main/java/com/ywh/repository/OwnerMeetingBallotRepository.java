package com.ywh.repository;

import com.ywh.entity.OwnerMeetingBallot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwnerMeetingBallotRepository extends JpaRepository<OwnerMeetingBallot, Long> {
    Optional<OwnerMeetingBallot> findByMeetingId(Long meetingId);
}
