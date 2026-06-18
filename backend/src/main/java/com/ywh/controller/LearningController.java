package com.ywh.controller;

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
    public Result<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "internal") String type,
            @RequestParam(required = false) String stage) {
        return Result.ok(service.list(type, stage));
    }

    @GetMapping("/counts")
    public Result<Map<String, Object>> counts(@RequestParam(defaultValue = "internal") String type) {
        return Result.ok(service.getCounts(type));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> req) {
        return Result.ok(service.create(req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }

    @PutMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        service.startLearning(id);
        return Result.ok();
    }

    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        service.finishLearning(id);
        return Result.ok();
    }

    // 通知全员
    @PostMapping("/{id}/notify-all")
    public Result<Void> notifyAll(@PathVariable Long id) {
        service.notifyAll(id);
        return Result.ok();
    }

    // 签到
    @PutMapping("/{id}/sign-in")
    public Result<Void> signIn(@PathVariable Long id) {
        service.signIn(id);
        return Result.ok();
    }

    // 佐证
    @PostMapping("/{id}/evidences")
    public Result<Map<String, Object>> addEvidence(@PathVariable Long id,
                                                    @RequestParam String fileName,
                                                    @RequestParam String fileType) {
        return Result.ok(service.addEvidence(id, fileName, fileType));
    }

    @DeleteMapping("/{id}/evidences/{evId}")
    public Result<Void> removeEvidence(@PathVariable Long id, @PathVariable Long evId) {
        service.removeEvidence(id, evId);
        return Result.ok();
    }
}
