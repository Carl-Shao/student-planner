package com.planner.controller;

import com.planner.common.Result;
import com.planner.dto.AuthDtos.*;
import com.planner.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    /** 发送注册验证码 */
    @PostMapping("/sms/send")
    public Result<Void> send(@Valid @RequestBody SendCodeReq req) {
        auth.sendCode(req.phone());
        return Result.ok();
    }

    /** 手机号 + 密码 + 验证码 注册 */
    @PostMapping("/register")
    public Result<AuthResp> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(auth.register(req));
    }

    /** 手机号 + 密码 登录 */
    @PostMapping("/login")
    public Result<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(auth.login(req));
    }
}
