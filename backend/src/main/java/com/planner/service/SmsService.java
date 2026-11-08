package com.planner.service;

import com.planner.client.sms.SmsSender;
import com.planner.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 短信验证码（通道由 SmsSender 实现决定）：
 *  - sms:limit:{phone}  60 秒（发送间隔，SETNX 原子保证）
 *  - sms:code:{phone}   5 分钟（验证码）
 *  - sms:fail:{phone}   5 分钟（验证码错误次数，>=5 次作废）
 *  - sms:day:{phone}    24 小时（每日最多 10 条）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DAILY_LIMIT = 10;

    private final StringRedisTemplate redis;
    private final SmsSender sender;

    public void sendCode(String phone) {
        String limitKey = "sms:limit:" + phone;
        Boolean first = redis.opsForValue().setIfAbsent(limitKey, "1", Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(first)) {
            Long ttl = redis.getExpire(limitKey);
            throw new BizException(429, "发送过于频繁，请" + (ttl == null || ttl < 0 ? 60 : ttl) + "秒后再试");
        }

        String dayKey = "sms:day:" + phone;
        Long n = redis.opsForValue().increment(dayKey);
        if (n != null && n == 1) redis.expire(dayKey, Duration.ofHours(24));
        if (n != null && n > DAILY_LIMIT) throw new BizException(429, "今日验证码发送次数已达上限");

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeKey = "sms:code:" + phone;
        redis.opsForValue().set(codeKey, code, Duration.ofMinutes(5));
        redis.delete("sms:fail:" + phone);

        try {
            sender.send(phone, code);
        } catch (Exception e) {
            log.error("短信发送失败 phone={}", mask(phone), e);
            redis.delete(codeKey);
            redis.delete(limitKey);        // 发送失败不占用 1 分钟额度
            throw new BizException(502, "短信发送失败，请稍后重试");
        }
    }

    public void verifyCode(String phone, String code) {
        String codeKey = "sms:code:" + phone;
        String real = redis.opsForValue().get(codeKey);
        if (real == null) throw new BizException(400, "验证码已过期，请重新获取");

        String failKey = "sms:fail:" + phone;
        if (!real.equals(code)) {
            Long f = redis.opsForValue().increment(failKey);
            if (f != null && f == 1) redis.expire(failKey, Duration.ofMinutes(5));
            if (f != null && f >= 5) {
                redis.delete(codeKey);
                redis.delete(failKey);
                throw new BizException(400, "验证码错误次数过多，请重新获取");
            }
            throw new BizException(400, "验证码错误");
        }
        redis.delete(codeKey);   // 一次性
        redis.delete(failKey);
    }

    private static String mask(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
