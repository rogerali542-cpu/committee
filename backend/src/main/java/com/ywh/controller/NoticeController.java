package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.entity.Notice;
import com.ywh.service.NoticeService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService service;

    /** 居民端：小区首页公告卡片 */
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(service.listPublished());
    }

    /** 管理端：公告列表（含未发布）——仅主任/副主任 */
    @GetMapping("/manage")
    @RequireRole({"主任", "副主任"})
    public Result<List<Map<String, Object>>> manageList() {
        return Result.ok(service.listAll());
    }

    @PostMapping
    @RequireRole({"主任", "副主任"})
    public Result<Notice> create(@RequestBody Map<String, Object> req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        service.update(id, req);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }
}
