package com.ywh.repository;

import com.ywh.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 居民端：某小区已发布的公告，按公告日期倒序 */
    List<Notice> findByCommunityIdAndPublishedTrueOrderByNoticeDateDescIdDesc(Long communityId);

    /** 管理端：某小区全部公告（含未发布），按公告日期倒序 */
    List<Notice> findByCommunityIdOrderByNoticeDateDescIdDesc(Long communityId);

    long countByCommunityId(Long communityId);
}
