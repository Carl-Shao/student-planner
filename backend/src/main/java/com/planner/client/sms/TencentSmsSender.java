package com.planner.client.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 腾讯云短信 SendSms（API 3.0，签名 TC3-HMAC-SHA256），无需 SDK 依赖。
 * 需要：短信应用 SdkAppId、已审核签名与正文模板。模板参数约定：{1}=验证码，{2}=有效分钟数（例如“验证码{1}，{2}分钟内有效”）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sms", name = "provider", havingValue = "tencent")
public class TencentSmsSender implements SmsSender {
    private static final String HOST = "sms.tencentcloudapi.com";
    private static final String SERVICE = "sms";
    private static final String ACTION = "SendSms";
    private static final String VERSION = "2021-01-11";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String SIGNED_HEADERS = "content-type;host;x-tc-action";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${app.sms.tencent.secret-id}") private String secretId;
    @Value("${app.sms.tencent.secret-key}") private String secretKey;
    @Value("${app.sms.tencent.sdk-app-id}") private String sdkAppId;
    @Value("${app.sms.tencent.sign-name}") private String signName;
    @Value("${app.sms.tencent.template-id}") private String templateId;
    @Value("${app.sms.tencent.region}") private String region;

    @PostConstruct
    void check() {
        if (secretId.isBlank() || secretKey.isBlank() || sdkAppId.isBlank() || signName.isBlank() || templateId.isBlank()) {
            throw new IllegalStateException("app.sms.provider=tencent，但 app.sms.tencent.* 配置不完整");
        }
    }

    @Override
    public void send(String phone, String code) throws Exception {
        long ts = Instant.now().getEpochSecond();
        String date = DAY.format(Instant.ofEpochSecond(ts));

        ObjectNode b = om.createObjectNode();
        b.putArray("PhoneNumberSet").add("+86" + phone);
        b.put("SmsSdkAppId", sdkAppId);
        b.put("SignName", signName);
        b.put("TemplateId", templateId);
        b.putArray("TemplateParamSet").add(code).add("5");
        String payload = om.writeValueAsString(b);

        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\nhost:" + HOST + "\nx-tc-action:" + ACTION.toLowerCase() + "\n";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + SIGNED_HEADERS + "\n" + sha256Hex(payload);
        String scope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + ts + "\n" + scope + "\n" + sha256Hex(canonicalRequest);

        byte[] kDate = hmac(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kService = hmac(kDate, SERVICE);
        byte[] kSigning = hmac(kService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmac(kSigning, stringToSign));
        String auth = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + scope
                + ", SignedHeaders=" + SIGNED_HEADERS + ", Signature=" + signature;

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://" + HOST + "/"))   // Host 头由 URI 自动生成
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", auth)
                .header("Content-Type", CONTENT_TYPE)
                .header("X-TC-Action", ACTION)
                .header("X-TC-Version", VERSION)
                .header("X-TC-Timestamp", String.valueOf(ts))
                .header("X-TC-Region", region)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        JsonNode r = om.readTree(resp.body()).path("Response");
        if (r.has("Error")) {
            throw new IllegalStateException("Tencent SMS 失败: " + r.at("/Error/Code").asText() + " " + r.at("/Error/Message").asText());
        }
        JsonNode st = r.path("SendStatusSet").path(0);
        if (!"Ok".equals(st.path("Code").asText())) {
            throw new IllegalStateException("Tencent SMS 失败: " + st.path("Code").asText() + " " + st.path("Message").asText());
        }
    }

    private static byte[] hmac(byte[] key, String msg) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
