package com.ywh.repository;

import com.ywh.entity.ReceptionSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReceptionSystemRepository extends JpaRepository<ReceptionSystem, Long> {
    Optional<ReceptionSystem> findByCommunityId(Long communityId);
}
