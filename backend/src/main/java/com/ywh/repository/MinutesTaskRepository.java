package com.ywh.repository;

import com.ywh.entity.MinutesTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MinutesTaskRepository extends JpaRepository<MinutesTask, Long> {
    /** 某会议最新的一条纪要生成任务（按 id 倒序取第一条）。 */
    Optional<MinutesTask> findTopByMeetingIdOrderByIdDesc(Long meetingId);
}
