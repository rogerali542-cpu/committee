package com.ywh.service.quick;

import com.ywh.dto.NewsVO;
import com.ywh.repository.CommitteeMeetingRepository;
import com.ywh.repository.MeetingRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 党建新闻生成的后台执行体（@Async）。单独成 bean，避免与 {@link NewsTaskService} 的自调用绕过代理。
 * 跑在 Spring task executor 线程上，彻底脱离 HTTP 请求线程 → 客户端退微信/锁屏/断连都不影响它跑到底并落库。
 *
 * ⚠ 后台线程没有请求的 SecurityContext（getCurrentUserRole()=null），不能调
 * CommitteeService.getDetail/generateMinutes 这类按当前用户鉴权的方法（会 NPE）。
 * 权限已在发起端点 @RequireRole 校验过，这里直接读库拿标题+纪要正文即可。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsAsyncWorker {

    private final CommitteeMeetingRepository meetingRepo;
    private final MeetingRecordRepository recordRepo;
    private final NewsGenService newsGenService;
    private final NewsTaskService newsTaskService;

    @Async
    public void generate(Long meetingId) {
        try {
            String title = meetingRepo.findById(meetingId)
                    .map(m -> m.getTitle()).orElse(null);
            String minutes = recordRepo.findByMeetingId(meetingId)
                    .map(r -> r.getMinutesText()).orElse("");
            NewsVO vo = newsGenService.generate(title, minutes == null ? "" : minutes);
            boolean ok = vo != null && vo.getContent() != null && !vo.getContent().isBlank();
            newsTaskService.markDone(meetingId, ok,
                    ok ? vo.getTitle() : null,
                    ok ? vo.getContent() : null,
                    ok ? null : "生成结果为空");
        } catch (Exception e) {
            log.warn("[news-async] 生成失败 meeting={}: {}", meetingId, e.getMessage());
            newsTaskService.markDone(meetingId, false, null, null, e.getMessage());
        }
    }
}
