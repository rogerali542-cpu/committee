package com.ywh.service;

import com.ywh.entity.Community;
import com.ywh.entity.Notice;
import com.ywh.repository.CommunityRepository;
import com.ywh.repository.NoticeRepository;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepo;
    private final CommunityRepository communityRepo;

    /** 居民端：当前小区已发布公告 */
    public List<Map<String, Object>> listPublished() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return noticeRepo.findByCommunityIdAndPublishedTrueOrderByNoticeDateDescIdDesc(communityId)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 管理端：当前小区全部公告（含未发布） */
    public List<Map<String, Object>> listAll() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return noticeRepo.findByCommunityIdOrderByNoticeDateDescIdDesc(communityId)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public Notice create(Map<String, Object> req) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        Community community = communityRepo.findById(communityId)
                .orElseThrow(() -> new IllegalArgumentException("小区不存在"));

        String title = str(req.get("title"));
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("公告标题不能为空");
        }

        Notice notice = Notice.builder()
                .community(community)
                .title(title.trim())
                .detail(str(req.get("detail")))
                .noticeDate(parseDate(req.get("date"), req.get("noticeDate")))
                .published(req.get("published") == null ? Boolean.TRUE : Boolean.valueOf(String.valueOf(req.get("published"))))
                .build();
        return noticeRepo.save(notice);
    }

    @Transactional
    public void update(Long id, Map<String, Object> req) {
        Notice notice = getOwned(id);
        if (req.containsKey("title")) {
            String title = str(req.get("title"));
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("公告标题不能为空");
            }
            notice.setTitle(title.trim());
        }
        if (req.containsKey("detail")) {
            notice.setDetail(str(req.get("detail")));
        }
        if (req.get("date") != null || req.get("noticeDate") != null) {
            notice.setNoticeDate(parseDate(req.get("date"), req.get("noticeDate")));
        }
        if (req.containsKey("published")) {
            notice.setPublished(Boolean.valueOf(String.valueOf(req.get("published"))));
        }
        noticeRepo.save(notice);
    }

    @Transactional
    public void remove(Long id) {
        noticeRepo.delete(getOwned(id));
    }

    /** 取属于当前小区的公告，越权则报错 */
    private Notice getOwned(Long id) {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        Notice notice = noticeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("公告不存在"));
        if (!notice.getCommunity().getId().equals(communityId)) {
            throw new IllegalArgumentException("无权操作该公告");
        }
        return notice;
    }

    private Map<String, Object> toVO(Notice n) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", n.getId());
        vo.put("date", n.getNoticeDate() != null ? n.getNoticeDate().toString() : "");
        vo.put("title", n.getTitle());
        vo.put("detail", n.getDetail());
        vo.put("published", n.getPublished());
        return vo;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static LocalDate parseDate(Object primary, Object fallback) {
        Object v = primary != null ? primary : fallback;
        if (v == null || String.valueOf(v).isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(String.valueOf(v));
    }
}
