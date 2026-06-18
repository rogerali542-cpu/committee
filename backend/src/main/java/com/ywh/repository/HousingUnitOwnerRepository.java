package com.ywh.repository;

import com.ywh.entity.HousingUnitOwner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface HousingUnitOwnerRepository extends JpaRepository<HousingUnitOwner, Long> {
    List<HousingUnitOwner> findByUnitId(Long unitId);

    /** 查某户所有产权人的 user_role_id */
    @Query("SELECT o.userRoleId FROM HousingUnitOwner o WHERE o.unitId = :unitId")
    List<Long> findOwnerIdsByUnitId(Long unitId);

    /** 查某小区所有产权人（去重），用于发通知 */
    @Query("SELECT DISTINCT o.userRoleId FROM HousingUnitOwner o WHERE o.unitId IN " +
           "(SELECT h.id FROM HousingUnit h WHERE h.communityId = :communityId)")
    List<Long> findDistinctOwnerIdsByCommunity(Long communityId);
}
