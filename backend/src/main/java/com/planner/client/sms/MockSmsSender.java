package com.planner.client.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {
    @Override
    public void send(String phone, String code) {
        log.info("[MOCK SMS] phone={} code={}  (仅开发环境；生产请设置 SMS_PROVIDER=aliyun 或 tencent)", phone, code);
    }
}
