package com.ywh.controller;

import com.ywh.annotation.RequirePermission;
import com.ywh.enums.SystemPermission;
import com.ywh.service.RoleManagementService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/role-management")
@RequiredArgsConstructor
public class RoleManagementController {
    private final RoleManagementService service;

    @GetMapping("/secretaries")
    @RequirePermission(SystemPermission.SECRETARY_MANAGE)
    public Result<List<Map<String, Object>>> secretaries() {
        return Result.ok(service.listSecretaries());
    }

    @PostMapping("/secretaries/{id}/authorize")
    @RequirePermission(SystemPermission.SECRETARY_MANAGE)
    public Result<Map<String, Object>> authorize(@PathVariable Long id) {
        return Result.ok(service.authorizeSecretary(id));
    }

    @PostMapping("/secretaries/{id}/revoke")
    @RequirePermission(SystemPermission.SECRETARY_MANAGE)
    public Result<Map<String, Object>> revoke(@PathVariable Long id) {
        return Result.ok(service.revokeSecretary(id));
    }
}
