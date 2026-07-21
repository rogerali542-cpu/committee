package com.ywh.service.quick;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「谁正在现场录音」的轻量在册表（内存态，无需持久化）。
 * 录音端每 10s 心跳一次；25s 内有心跳即视为"正在录"。用途：
 * 第二个人点「开始录音」时前端先查这里，提示"XX 正在录音"，避免两路重复录音污染转写。
 * 心跳丢失（杀进程/断网）由 TTL 自然过期兜底，无需显式下线。
 */
@Service
public class RecordingLiveService {

    private static final long TTL_MS = 25_000;

    private record Beat(String name, long at) {}

    // meetingId -> { roleId -> 最近一次心跳 }
    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, Beat>> live = new ConcurrentHashMap<>();

    /** 记一次心跳（开始录音时立即打一次，之后周期打）。 */
    public void beat(Long meetingId, Long roleId, String name) {
        if (meetingId == null || roleId == null) return;
        live.computeIfAbsent(meetingId, k -> new ConcurrentHashMap<>())
                .put(roleId, new Beat(name == null ? "" : name, System.currentTimeMillis()));
    }

    /** 主动下线（暂停/停止/上传后）。 */
    public void stop(Long meetingId, Long roleId) {
        ConcurrentHashMap<Long, Beat> m = live.get(meetingId);
        if (m != null && roleId != null) m.remove(roleId);
    }

    /** 当前仍在录音的人（顺带清理过期项）。 */
    public List<Map<String, Object>> active(Long meetingId) {
        List<Map<String, Object>> out = new ArrayList<>();
        ConcurrentHashMap<Long, Beat> m = live.get(meetingId);
        if (m == null) return out;
        long now = System.currentTimeMillis();
        m.forEach((roleId, b) -> {
            if (now - b.at() > TTL_MS) m.remove(roleId);
            else out.add(Map.of("roleId", roleId, "name", b.name()));
        });
        return out;
    }
}
