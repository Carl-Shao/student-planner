package com.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AuthDtos {
    private AuthDtos() {}

    private static final String PHONE = "^1[3-9]\\d{9}$";
    private static final String PWD = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$";

    public record SendCodeReq(
            @NotBlank(message = "手机号不能为空") @Pattern(regexp = PHONE, message = "手机号格式不正确") String phone) {}

    public record RegisterReq(
            @NotBlank(message = "手机号不能为空") @Pattern(regexp = PHONE, message = "手机号格式不正确") String phone,
            @NotBlank(message = "密码不能为空") @Pattern(regexp = PWD, message = "密码需8-32位，且同时包含字母和数字") String password,
            @NotBlank(message = "验证码不能为空") @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字") String code) {}

    public record LoginReq(
            @NotBlank(message = "手机号不能为空") @Pattern(regexp = PHONE, message = "手机号格式不正确") String phone,
            @NotBlank(message = "密码不能为空") String password) {}

    public record AuthResp(String token, Long userId, boolean assessmentDone) {}
}
