package com.ywh.repository;

import com.ywh.entity.MeetingRecording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingRecordingRepository extends JpaRepository<MeetingRecording, Long> {

    List<MeetingRecording> findByMeetingIdOrderByCreatedAtDesc(Long meetingId);

    List<MeetingRecording> findByMeetingIdAndAsrStatusOrderByCreatedAtDesc(Long meetingId, String asrStatus);
}
