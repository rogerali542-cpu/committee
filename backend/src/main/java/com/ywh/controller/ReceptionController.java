package com.ywh.controller;

import com.ywh.entity.ReceptionRecord;
import com.ywh.service.ReceptionService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/receptions")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService service;

    @GetMapping("/system")
    public Result<Map<String, Object>> getSystem() {
        return Result.ok(service.getSystem());
    }

    @PutMapping("/system")
    public Result<Void> updateSystem(@RequestBody Map<String, Object> req) {
        service.updateSystem(req);
        return Result.ok();
    }

    @PostMapping("/system/toggle-publish")
    public Result<Void> togglePublish() {
        service.togglePublished();
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "all") String filter) {
        return Result.ok(service.listRecords(filter));
    }

    @PostMapping("/records")
    public Result<ReceptionRecord> create(@RequestBody Map<String, Object> req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/records/{id}/resolution")
    public Result<Void> updateResolution(@PathVariable Long id, @RequestParam String resolution) {
        service.updateResolution(id, resolution);
        return Result.ok();
    }

    @PutMapping("/records/{id}/follow")
    public Result<Void> toggleFollow(@PathVariable Long id, @RequestParam String field) {
        service.toggleFollow(id, field);
        return Result.ok();
    }

    @GetMapping("/property-tasks")
    public Result<List<Map<String, Object>>> propertyTasks(@RequestParam(required = false) String status) {
        return Result.ok(service.listPropertyTasks(status));
    }

    @GetMapping("/property-public")
    public Result<List<Map<String, Object>>> propertyPublic() {
        return Result.ok(service.listPropertyPublic());
    }

    @PostMapping("/records/{id}/dispatch")
    public Result<Void> dispatch(@PathVariable Long id) {
        service.dispatchToProperty(id);
        return Result.ok();
    }

    @PostMapping("/records/{id}/property-start")
    public Result<Void> propertyStart(@PathVariable Long id) {
        service.startProcessing(id);
        return Result.ok();
    }

    @PostMapping("/records/{id}/property-reply")
    public Result<Void> propertyReply(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        service.propertyReply(id, req != null ? (String) req.get("reply") : null);
        return Result.ok();
    }

    @DeleteMapping("/records/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return Result.ok();
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(service.getStats());
    }

    @PostMapping("/records/{id}/evidences")
    public Result<Map<String, Object>> addEvidence(@PathVariable Long id,
                                                    @RequestParam String fileName,
                                                    @RequestParam String fileType) {
        return Result.ok(service.addEvidence(id, fileName, fileType));
    }

    @DeleteMapping("/records/{id}/evidences/{evId}")
    public Result<Void> removeEvidence(@PathVariable Long id, @PathVariable Long evId) {
        service.removeEvidence(id, evId);
        return Result.ok();
    }
}
