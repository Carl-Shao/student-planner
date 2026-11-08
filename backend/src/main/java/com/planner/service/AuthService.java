package com.planner.service;

import com.planner.common.BizException;
import com.planner.dto.AuthDtos.*;
import com.planner.entity.User;
import com.planner.repository.UserRepository;
import com.planner.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int MAX_LOGIN_FAIL = 5;

    private final UserRepository users;
    private final SmsService sms;
    private final JwtUtil jwt;
    private final StringRedisTemplate redis;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    public void sendCode(String phone) {
        if (users.existsByPhone(phone)) throw new BizException(409, "该手机号已注册，请直接登录");
        sms.sendCode(phone);
    }

    public AuthResp register(RegisterReq r) {
        if (users.existsByPhone(r.phone())) throw new BizException(409, "该手机号已注册，请直接登录");
        sms.verifyCode(r.phone(), r.code());

        User u = new User();
        u.setPhone(r.phone());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setLastLoginAt(LocalDateTime.now());
        try {
            users.save(u);
        } catch (DataIntegrityViolationException e) {   // 并发注册兜底（phone 有唯一索引）
            throw new BizException(409, "该手机号已注册，请直接登录");
        }
        return new AuthResp(jwt.create(u.getId()), u.getId(), false);
    }

    public AuthResp login(LoginReq r) {
        String failKey = "login:fail:" + r.phone();
        String c = redis.opsForValue().get(failKey);
        if (c != null && Integer.parseInt(c) >= MAX_LOGIN_FAIL) {
            throw new BizException(429, "尝试次数过多，请15分钟后再试");
        }
        User u = users.findByPhone(r.phone()).orElse(null);
        if (u == null || !encoder.matches(r.password(), u.getPasswordHash())) {
            Long n = redis.opsForValue().increment(failKey);
            if (n != null && n == 1) redis.expire(failKey, Duration.ofMinutes(15));
            throw new BizException(401, "手机号或密码错误");
        }
        redis.delete(failKey);
        u.setLastLoginAt(LocalDateTime.now());
        users.save(u);
        return new AuthResp(jwt.create(u.getId()), u.getId(), u.isAssessmentDone());
    }
}
