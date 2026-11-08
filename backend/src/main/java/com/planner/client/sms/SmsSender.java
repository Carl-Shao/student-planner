package com.planner.client.sms;

/** 短信发送通道。由 app.sms.provider 选择实现：mock | aliyun | tencent | http */
public interface SmsSender {
    /** 发送验证码短信；失败抛异常（SmsService 会回滚限流并返回 502） */
    void send(String phone, String code) throws Exception;
}
