package com.ywh.repository;

import com.ywh.entity.HousingUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;
import java.util.List;

public interface HousingUnitRepository extends JpaRepository<HousingUnit, Long> {
    List<HousingUnit> findByCommunityId(Long communityId);

    @Query("SELECT COUNT(h) FROM HousingUnit h WHERE h.communityId = :communityId")
    long countByCommunity(Long communityId);

    @Query("SELECT COALESCE(SUM(h.area), 0) FROM HousingUnit h WHERE h.communityId = :communityId")
    BigDecimal sumAreaByCommunity(Long communityId);

    /** 查某人代表的所有房屋（用于投票聚合） */
    List<HousingUnit> findByRepresentativeUserId(Long representativeUserId);

    /** 查某小区某人代表的房屋 */
    List<HousingUnit> findByCommunityIdAndRepresentativeUserId(Long communityId, Long representativeUserId);
}
