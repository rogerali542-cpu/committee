package com.ywh.repository;

import com.ywh.entity.NewsTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsTaskRepository extends JpaRepository<NewsTask, Long> {
    /** 某会议最新的一条党建新闻生成任务（按 id 倒序取第一条）。 */
    Optional<NewsTask> findTopByMeetingIdOrderByIdDesc(Long meetingId);
}
