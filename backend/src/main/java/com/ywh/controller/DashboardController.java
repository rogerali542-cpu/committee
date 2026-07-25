package com.ywh.controller;

import com.ywh.annotation.RequireRole;
import com.ywh.service.DashboardService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/stats")
    @RequireRole({"主任", "副主任", "委员", "记录员"})
    public Result<Map<String, Object>> stats() {
        return Result.ok(service.getStats());
    }
}
