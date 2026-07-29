package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.service.LearningService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService service;

    @GetMapping
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "internal") String type,
            @RequestParam(required = false) String stage) {
        return Result.ok(service.list(type, stage));
    }

    @GetMapping("/counts")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<Map<String, Object>> counts(@RequestParam(defaultValue = "internal") String type) {
        return Result.ok(service.getCounts(type));
    }

    @PostMapping
    @RequireRole({"主任", "副主任"})
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> req) {
        return Result.ok(service.create(req));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }

    @PutMapping("/{id}/start")
    @RequireRole({"主任", "副主任"})
    public Result<Void> start(@PathVariable Long id) {
        service.startLearning(id);
        return Result.ok();
    }

    @PutMapping("/{id}/finish")
    @RequireRole({"主任", "副主任"})
    public Result<Void> finish(@PathVariable Long id) {
        service.finishLearning(id);
        return Result.ok();
    }

    // 修改分类：内部学习 / 外部培训
    @PutMapping("/{id}/category")
    @RequireRole({"主任", "副主任"})
    public Result<Void> setCategory(@PathVariable Long id, @RequestParam String category) {
        service.setCategory(id, category);
        return Result.ok();
    }

    // 通知全员；可选 body { names: [...], channel: "app"|"wechat" }：参加人员落库 + 通知留痕
    @PostMapping("/{id}/notify-all")
    @RequireRole({"主任", "副主任"})
    public Result<Void> notifyAll(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        List<String> names = null;
        String channel = null;
        if (body != null) {
            if (body.get("names") instanceof List<?> raw) {
                names = raw.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
            }
            if (body.get("channel") != null) channel = String.valueOf(body.get("channel"));
        }
        service.notifyAll(id, names, channel);
        return Result.ok();
    }

    // 签到
    @PutMapping("/{id}/sign-in")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<Void> signIn(@PathVariable Long id) {
        service.signIn(id);
        return Result.ok();
    }

    @PutMapping("/{id}/attendance")
    @RequireRole({"主任", "副主任"})
    public Result<Void> setAttendance(@PathVariable Long id, @RequestBody Map<String, List<String>> req) {
        service.setAttendance(id, req.get("attendedNames"));
        return Result.ok();
    }

    // 佐证
    @PostMapping("/{id}/evidences")
    @RequireRole({"主任", "副主任"})
    public Result<Map<String, Object>> addEvidence(@PathVariable Long id,
                                                    @RequestParam String fileName,
                                                    @RequestParam String fileType,
                                                    @RequestParam(required = false) String fileUrl) {
        return Result.ok(service.addEvidence(id, fileName, fileType, fileUrl));
    }

    @DeleteMapping("/{id}/evidences/{evId}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> removeEvidence(@PathVariable Long id, @PathVariable Long evId) {
        service.removeEvidence(id, evId);
        return Result.ok();
    }
}
