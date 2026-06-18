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
}
