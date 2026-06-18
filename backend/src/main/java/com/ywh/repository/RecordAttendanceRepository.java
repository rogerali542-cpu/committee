package com.ywh.repository;

import com.ywh.entity.RecordAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RecordAttendanceRepository extends JpaRepository<RecordAttendance, Long> {
    List<RecordAttendance> findByRecordId(Long recordId);
    Optional<RecordAttendance> findByRecordIdAndUserRoleId(Long recordId, Long userRoleId);
}
