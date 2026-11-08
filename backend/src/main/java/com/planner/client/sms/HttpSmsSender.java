package com.planner.client.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** 通用 HTTP 网关（自建短信服务/第三方聚合平台）：POST JSON，Bearer 鉴权。 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "http")
public class HttpSmsSender implements SmsSender {
    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${app.sms.http.api-url}") private String apiUrl;
    @Value("${app.sms.http.api-key}") private String apiKey;
    @Value("${app.sms.http.template-id}") private String templateId;
    @Value("${app.sms.http.sign-name}") private String signName;

    @Override
    public void send(String phone, String code) throws Exception {
        Map<String, Object> body = Map.of(
                "phone", phone, "templateId", templateId, "signName", signName,
                "params", Map.of("code", code, "ttlMinutes", "5"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("SMS gateway HTTP " + resp.statusCode() + ": " + resp.body());
        }
    }
}
