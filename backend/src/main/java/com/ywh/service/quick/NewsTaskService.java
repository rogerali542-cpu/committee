package com.ywh.service.quick;

import com.ywh.dto.quick.NewsTaskStatusVO;
import com.ywh.entity.NewsTask;
import com.ywh.repository.NewsTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 党建新闻生成任务的状态存取（落库成服务端可查状态 + 结果）。仿纪要 markMinutesTask*。
 * 生成本体走 {@link NewsAsyncWorker} 的 @Async 后台线程，与本类的短事务分离。
 */
@Service
@RequiredArgsConstructor
public class NewsTaskService {

    private final NewsTaskRepository repo;

    /** 生成开始：30s 内仍 running 的任务视为在跑（去重，返回 false 不重复起）；否则新建 running 返回 true。 */
    @Transactional
    public boolean markRunningIfNeeded(Long meetingId) {
        NewsTask t = repo.findTopByMeetingIdOrderByIdDesc(meetingId).orElse(null);
        if (t != null && "running".equals(t.getStatus()) && t.getUpdatedAt() != null
                && t.getUpdatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
            return false;
        }
        repo.save(NewsTask.builder().meetingId(meetingId).status("running").build());
        return true;
    }

    /** 生成结束：把最新任务标记 success（存标题/正文）/failed（存错误）。 */
    @Transactional
    public void markDone(Long meetingId, boolean success, String title, String content, String err) {
        repo.findTopByMeetingIdOrderByIdDesc(meetingId).ifPresent(t -> {
            t.setStatus(success ? "success" : "failed");
            if (success) {
                t.setTitle(title != null && title.length() > 500 ? title.substring(0, 500) : title);
                t.setContent(content);
            }
            if (err != null) t.setErrorMsg(err.length() > 500 ? err.substring(0, 500) : err);
            repo.save(t);
        });
    }

    /** 最新新闻任务状态（none/running/success/failed）；running 超 6 分钟没更新视为 failed（卡死兜底）。success 带回标题/正文。 */
    @Transactional(readOnly = true)
    public NewsTaskStatusVO getStatus(Long meetingId) {
        NewsTask t = repo.findTopByMeetingIdOrderByIdDesc(meetingId).orElse(null);
        if (t == null) return NewsTaskStatusVO.builder().status("none").build();
        String status = t.getStatus();
        if ("running".equals(status) && t.getUpdatedAt() != null
                && t.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(6))) {
            status = "failed";
        }
        boolean ok = "success".equals(status);
        return NewsTaskStatusVO.builder()
                .status(status)
                .taskId(t.getId())
                .title(ok ? t.getTitle() : null)
                .content(ok ? t.getContent() : null)
                .build();
    }
}
