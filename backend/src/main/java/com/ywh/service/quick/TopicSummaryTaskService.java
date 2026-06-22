package com.ywh.service.quick;

import com.ywh.dto.quick.TopicSummaryTaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicSummaryTaskService {

    private static final long KEEP_MS = 30L * 60L * 1000L;

    private final MinutesGenService minutesGenService;
    private final ConcurrentHashMap<String, TaskState> tasks = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "topic-summary");
        t.setDaemon(true);
        return t;
    });

    public TopicSummaryTaskVO submit(Long meetingId, String title, String type, List<String> texts) {
        cleanup();
        String taskId = "topic_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        TaskState state = new TaskState(taskId, meetingId, title, type);
        tasks.put(taskId, state);
        List<String> snapshot = texts == null ? List.of() : List.copyOf(texts);
        pool.submit(() -> run(state, snapshot));
        return state.toVo();
    }

    public TopicSummaryTaskVO status(String taskId) {
        cleanup();
        TaskState state = tasks.get(taskId);
        if (state == null) {
            long now = System.currentTimeMillis();
            return TopicSummaryTaskVO.builder()
                    .taskId(taskId)
                    .status("failed")
                    .message("任务不存在或已过期，请重新生成")
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }
        return state.toVo();
    }

    private void run(TaskState state, List<String> texts) {
        state.running();
        try {
            String result = minutesGenService.summarizeTopic(state.title, state.type, texts);
            if (result == null || result.isBlank()) {
                state.failed("大模型未返回有效内容，请重新生成");
            } else {
                state.succeeded(result.trim());
            }
        } catch (Exception e) {
            log.error("[TOPIC-SUMMARY] 议题报告任务失败 taskId={}", state.taskId, e);
            state.failed(e.getMessage() == null ? "生成失败" : e.getMessage());
        }
    }

    private void cleanup() {
        long threshold = System.currentTimeMillis() - KEEP_MS;
        tasks.entrySet().removeIf(e -> e.getValue().updatedAt < threshold);
    }

    private static final class TaskState {
        private final String taskId;
        private final Long meetingId;
        private final String title;
        private final String type;
        private final long createdAt;
        private volatile long updatedAt;
        private volatile String status;
        private volatile String result;
        private volatile String message;

        private TaskState(String taskId, Long meetingId, String title, String type) {
            this.taskId = taskId;
            this.meetingId = meetingId;
            this.title = title;
            this.type = type;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
            this.status = "queued";
            this.message = "已加入生成队列";
        }

        private void running() {
            this.status = "running";
            this.message = "大模型生成中";
            this.updatedAt = System.currentTimeMillis();
        }

        private void succeeded(String result) {
            this.status = "succeeded";
            this.result = result;
            this.message = "生成完成";
            this.updatedAt = System.currentTimeMillis();
        }

        private void failed(String message) {
            this.status = "failed";
            this.message = message;
            this.updatedAt = System.currentTimeMillis();
        }

        private TopicSummaryTaskVO toVo() {
            return TopicSummaryTaskVO.builder()
                    .taskId(taskId)
                    .meetingId(meetingId)
                    .status(status)
                    .title(title)
                    .type(type)
                    .result(result)
                    .message(message)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();
        }
    }
}
