package com.ywh.controller;

import com.ywh.annotation.RequirePermission;
import com.ywh.entity.UserRoleEntity;
import com.ywh.enums.SystemPermission;
import com.ywh.util.Result;
import com.ywh.util.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 街道/区级管理端接口占位。真实小区坐标、评分和提醒数据接入后扩展。 */
@RestController
@RequestMapping("/api/management")
public class ManagementOverviewController {

    @GetMapping("/overview")
    @RequirePermission(SystemPermission.MANAGEMENT_OVERVIEW)
    public Result<Map<String, Object>> overview() {
        UserRoleEntity identity = SecurityUtils.getCurrentUserRole();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeLevel", identity.getScopeLevel().name());
        result.put("scopeRegionCode", identity.getScopeRegionCode());
        result.put("scopeRegionName", identity.getScopeRegionName());
        result.put("communities", List.of());
        result.put("reminders", List.of());
        result.put("dataReady", false);
        return Result.ok(result);
    }
}
