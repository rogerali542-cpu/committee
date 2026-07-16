package com.ywh.controller;

import com.ywh.dto.MeetingTodoTicketVO;
import com.ywh.entity.ReceptionRecord;
import com.ywh.service.ReceptionService;
import com.ywh.service.ReceptionTicketService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 接待。0716 重做（方案 A）：内部派单流的 5 个端点已下线
 * （/property-tasks、/property-public、/records/{id}/dispatch、/property-start、/property-reply），
 * 改为 POST /records/{id}/ticket 派发到外部工单系统。
 * 同时清掉两个无前端调用方的死端点：/system/toggle-publish、/records/{id}/follow。
 * 原实现见 commit 6745a12。
 */
@RestController
@RequestMapping("/api/receptions")
@RequiredArgsConstructor
public class ReceptionController {

    private final ReceptionService service;
    private final ReceptionTicketService ticketService;

    @GetMapping("/system")
    public Result<Map<String, Object>> getSystem() {
        return Result.ok(service.getSystem());
    }

    @PutMapping("/system")
    public Result<Void> updateSystem(@RequestBody Map<String, Object> req) {
        service.updateSystem(req);
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<List<Map<String, Object>>> list(@RequestParam(defaultValue = "all") String filter) {
        return Result.ok(service.listRecords(filter));
    }

    /** 单条详情：新的「单条处理页」进来就拉这个。 */
    @GetMapping("/records/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Result.ok(service.getRecord(id));
    }

    @PostMapping("/records")
    public Result<ReceptionRecord> create(@RequestBody Map<String, Object> req) {
        return Result.ok(service.create(req));
    }

    /** 填写处理结果 —— 这就是办结动作（done 以它为准）。 */
    @PutMapping("/records/{id}/resolution")
    public Result<Void> updateResolution(@PathVariable Long id, @RequestParam String resolution) {
        service.updateResolution(id, resolution);
        return Result.ok();
    }

    /** 派发到外部工单系统。幂等：重复调用返回对方已有工单，不会重复建单。 */
    @PostMapping("/records/{id}/ticket")
    public Result<MeetingTodoTicketVO> pushTicket(@PathVariable Long id) {
        return Result.ok(ticketService.push(id));
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
                                                    @RequestParam String fileType,
                                                    @RequestParam(required = false) String fileUrl) {
        return Result.ok(service.addEvidence(id, fileName, fileType, fileUrl));
    }

    @DeleteMapping("/records/{id}/evidences/{evId}")
    public Result<Void> removeEvidence(@PathVariable Long id, @PathVariable Long evId) {
        service.removeEvidence(id, evId);
        return Result.ok();
    }
}
