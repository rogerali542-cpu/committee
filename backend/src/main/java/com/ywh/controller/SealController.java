package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.entity.SealUseRecord;
import com.ywh.service.SealService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 印章管理：用印申请 + 使用记录留档（用印台账）。
 * 依据《日常工作制度汇编·印章管理制度》：印章三枚，由主任、副主任分人保管；
 * 用印须登记时间、用途、文件、申请人，并经保管人（主任/副主任）确认。
 * 申请：委员及以上均可；确认/驳回：仅保管人（主任、副主任）；删除：仅主任。
 */
@RestController
@RequestMapping("/api/seals")
@RequiredArgsConstructor
public class SealController {

    private final SealService service;

    /** 印章清单（固定三枚）。 */
    @GetMapping("/list")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<List<Map<String, Object>>> seals() {
        return Result.ok(service.listSeals());
    }

    /** 用印台账。filter = all | pending | approved */
    @GetMapping("/records")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<List<Map<String, Object>>> records(@RequestParam(defaultValue = "all") String filter) {
        return Result.ok(service.listRecords(filter));
    }

    @GetMapping("/stats")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<Map<String, Object>> stats() {
        return Result.ok(service.getStats());
    }

    /** 申请用印。 */
    @PostMapping("/records")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<SealUseRecord> apply(@RequestBody Map<String, Object> req) {
        return Result.ok(service.apply(req));
    }

    /** 保管人确认用印（盖章留档）。仅主任 / 副主任。 */
    @PutMapping("/records/{id}/confirm")
    @RequireRole({"主任", "副主任"})
    public Result<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return Result.ok();
    }

    /** 保管人驳回。仅主任 / 副主任。 */
    @PutMapping("/records/{id}/reject")
    @RequireRole({"主任", "副主任"})
    public Result<Void> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        service.reject(id, reason);
        return Result.ok();
    }

    @DeleteMapping("/records/{id}")
    @RequireRole({"主任"})
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }
}
