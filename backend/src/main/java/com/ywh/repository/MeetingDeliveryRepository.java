package com.ywh.repository;

import com.ywh.entity.MeetingDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface MeetingDeliveryRepository extends JpaRepository<MeetingDelivery, Long> {
    List<MeetingDelivery> findByMeetingId(Long meetingId);
    Optional<MeetingDelivery> findByMeetingIdAndUserRoleId(Long meetingId, Long userRoleId);

    @Modifying
    @Query("delete from MeetingDelivery d where d.meeting.id = :meetingId")
    void deleteByMeetingId(@Param("meetingId") Long meetingId);
}
