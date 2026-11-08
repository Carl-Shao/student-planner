package com.planner.client.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 阿里云短信 Dysmsapi SendSms（RPC 风格，签名 v1.0 / HMAC-SHA1），无需 SDK 依赖。
 * 需要：已审核通过的签名 + 模板（模板变量名必须是 ${code}），RAM 子账号 AccessKey（仅授予短信发送权限）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "aliyun")
public class AliyunSmsSender implements SmsSender {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${app.sms.aliyun.access-key-id}") private String accessKeyId;
    @Value("${app.sms.aliyun.access-key-secret}") private String accessKeySecret;
    @Value("${app.sms.aliyun.sign-name}") private String signName;
    @Value("${app.sms.aliyun.template-code}") private String templateCode;

    @PostConstruct
    void check() {
        if (accessKeyId.isBlank() || accessKeySecret.isBlank() || signName.isBlank() || templateCode.isBlank()) {
            throw new IllegalStateException("app.sms.provider=aliyun，但 app.sms.aliyun.* 配置不完整");
        }
    }

    @Override
    public void send(String phone, String code) throws Exception {
        Map<String, String> p = new TreeMap<>();          // 参数名按字典序排序
        p.put("AccessKeyId", accessKeyId);
        p.put("Action", "SendSms");
        p.put("Format", "JSON");
        p.put("PhoneNumbers", phone);
        p.put("RegionId", "cn-hangzhou");
        p.put("SignName", signName);
        p.put("SignatureMethod", "HMAC-SHA1");
        p.put("SignatureNonce", UUID.randomUUID().toString());
        p.put("SignatureVersion", "1.0");
        p.put("TemplateCode", templateCode);
        p.put("TemplateParam", om.writeValueAsString(Map.of("code", code)));
        p.put("Timestamp", TS.format(Instant.now()));
        p.put("Version", "2017-05-25");

        String canonical = p.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        String stringToSign = "POST&" + enc("/") + "&" + enc(canonical);

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        String sig = Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://dysmsapi.aliyuncs.com/"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(canonical + "&Signature=" + enc(sig)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode n = om.readTree(resp.body());
        if (!"OK".equals(n.path("Code").asText())) {
            throw new IllegalStateException("Aliyun SMS 失败: " + n.path("Code").asText() + " " + n.path("Message").asText());
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }
}
