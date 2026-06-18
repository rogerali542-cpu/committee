package com.ywh.repository;

import com.ywh.entity.LearningSignIn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LearningSignInRepository extends JpaRepository<LearningSignIn, Long> {
    List<LearningSignIn> findByRecordId(Long recordId);
    Optional<LearningSignIn> findByRecordIdAndRealName(Long recordId, String realName);
    long countByRecordIdAndSignedInTrue(Long recordId);
}
