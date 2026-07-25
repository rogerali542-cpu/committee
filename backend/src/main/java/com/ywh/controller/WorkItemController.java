package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.entity.WorkItem;
import com.ywh.service.WorkItemService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 事项基础接口。当前没有前端入口，也未接入阳光花园演示数据。
 */
@RestController
@RequestMapping("/api/work-items")
@RequiredArgsConstructor
public class WorkItemController {
    private final WorkItemService service;

    @GetMapping
    @RequireRole({"主任", "副主任"})
    public Result<List<WorkItem>> list(@RequestParam Long communityId,
                                      @RequestParam(defaultValue = "all") String status) {
        return Result.ok(service.list(communityId, status));
    }

    @GetMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<WorkItem> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    @RequireRole({"主任", "副主任"})
    public Result<WorkItem> create(@RequestBody WorkItem item) {
        return Result.ok(service.create(item));
    }

    @PutMapping("/{id}/status")
    @RequireRole({"主任", "副主任"})
    public Result<WorkItem> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> req) {
        return Result.ok(service.updateStatus(id, req));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"主任", "副主任"})
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }
}
