package com.ywh.controller;

import com.ywh.dto.LoginRequest;
import com.ywh.dto.LoginResponse;
import com.ywh.service.AuthService;
import com.ywh.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @GetMapping("/me")
    public Result<LoginResponse> me() {
        return Result.ok(authService.refreshMe());
    }

    /** 测试期身份名单（0723 根治"前端写死名单与库脱节"）：登录页/个人中心切换身份改从这里拉，
     *  名单以数据库 user_roles 为唯一事实源。⚠ 免登录暴露委员姓名，上线前需关闭（见 docs/上线前TODO.md）。 */
    @GetMapping("/dev-roles")
    public Result<java.util.List<java.util.Map<String, Object>>> devRoles() {
        return Result.ok(authService.listDevRoles());
    }
}
