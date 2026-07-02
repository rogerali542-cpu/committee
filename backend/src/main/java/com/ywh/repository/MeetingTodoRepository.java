package com.ywh.repository;

import com.ywh.entity.MeetingTodo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingTodoRepository extends JpaRepository<MeetingTodo, Long> {
    List<MeetingTodo> findByMeetingIdOrderBySortOrderAsc(Long meetingId);
    long countByMeetingId(Long meetingId);
}
