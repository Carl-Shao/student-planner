package com.planner.service;

import com.planner.client.sms.SmsSender;
import com.planner.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsServiceTest {
    private static final String PHONE = "13800138000";
    private static final String LIMIT = "sms:limit:" + PHONE;
    private static final String DAY = "sms:day:" + PHONE;
    private static final String CODE = "sms:code:" + PHONE;
    private static final String FAIL = "sms:fail:" + PHONE;

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final SmsSender sender = mock(SmsSender.class);
    private SmsService sms;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        sms = new SmsService(redis, sender);
    }

    private void allowSend() {
        when(ops.setIfAbsent(eq(LIMIT), eq("1"), any(Duration.class))).thenReturn(true);
        when(ops.increment(DAY)).thenReturn(1L);
    }

    @Test
    void sendWithinOneMinuteIsRejected() {
        when(ops.setIfAbsent(eq(LIMIT), eq("1"), any(Duration.class))).thenReturn(false);
        when(redis.getExpire(LIMIT)).thenReturn(42L);
        BizException e = assertThrows(BizException.class, () -> sms.sendCode(PHONE));
        assertEquals(429, e.getCode());
        assertTrue(e.getMessage().contains("42"));
        verifyNoInteractions(sender);
    }

    @Test
    void sendStoresCodeForFiveMinutesAndCallsGateway() throws Exception {
        allowSend();
        sms.sendCode(PHONE);

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(CODE), code.capture(), eq(Duration.ofMinutes(5)));
        assertTrue(code.getValue().matches("\\d{6}"));
        verify(sender).send(PHONE, code.getValue());
    }

    @Test
    void dailyLimitIsEnforced() {
        when(ops.setIfAbsent(eq(LIMIT), eq("1"), any(Duration.class))).thenReturn(true);
        when(ops.increment(DAY)).thenReturn(11L);
        BizException e = assertThrows(BizException.class, () -> sms.sendCode(PHONE));
        assertEquals(429, e.getCode());
        verifyNoInteractions(sender);
    }

    @Test
    void gatewayFailureReleasesLimitAndCode() throws Exception {
        allowSend();
        doThrow(new IllegalStateException("boom")).when(sender).send(any(), any());
        BizException e = assertThrows(BizException.class, () -> sms.sendCode(PHONE));
        assertEquals(502, e.getCode());
        verify(redis).delete(CODE);
        verify(redis).delete(LIMIT);
    }

    @Test
    void correctCodeIsConsumed() {
        when(ops.get(CODE)).thenReturn("123456");
        assertDoesNotThrow(() -> sms.verifyCode(PHONE, "123456"));
        verify(redis).delete(CODE);
    }

    @Test
    void expiredCodeIsRejected() {
        when(ops.get(CODE)).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> sms.verifyCode(PHONE, "123456"));
        assertEquals(400, e.getCode());
        assertTrue(e.getMessage().contains("过期"));
    }

    @Test
    void wrongCodeKeepsCodeUntilTooManyAttempts() {
        when(ops.get(CODE)).thenReturn("123456");
        when(ops.increment(FAIL)).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> sms.verifyCode(PHONE, "000000"));
        assertEquals("验证码错误", e.getMessage());
        verify(redis, never()).delete(CODE);
    }

    @Test
    void fifthWrongAttemptInvalidatesCode() {
        when(ops.get(CODE)).thenReturn("123456");
        when(ops.increment(FAIL)).thenReturn(5L);
        BizException e = assertThrows(BizException.class, () -> sms.verifyCode(PHONE, "000000"));
        assertTrue(e.getMessage().contains("过多"));
        verify(redis).delete(CODE);
    }
}
