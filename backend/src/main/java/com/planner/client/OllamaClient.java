package com.planner.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planner.dto.Msg;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 调用本地 Ollama 的 /api/chat（非流式 / 流式 NDJSON）。 */
@Component
@RequiredArgsConstructor
public class OllamaClient {
    private final ObjectMapper om;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${app.ollama.base-url}") private String baseUrl;
    @Value("${app.ollama.model}") private String model;
    @Value("${app.ollama.timeout-seconds}") private long timeoutSeconds;
    @Value("${app.ollama.temperature}") private double temperature;
    @Value("${app.ollama.num-ctx}") private int numCtx;
    @Value("${app.knowledge.embedding.model:bge-m3}") private String embedModel;

    /** @param json true 时要求模型只输出 JSON（Ollama format=json） */
    public String chat(List<Msg> messages, boolean json) throws IOException, InterruptedException {
        HttpResponse<String> resp = http.send(request(messages, false, json), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
        return om.readTree(resp.body()).path("message").path("content").asText("");
    }

    /** 流式：每收到一段文本调用一次 onToken；onToken 抛异常则中断。 */
    public void chatStream(List<Msg> messages, Consumer<String> onToken) throws IOException, InterruptedException {
        HttpResponse<Stream<String>> resp = http.send(request(messages, true, false), HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> lines = resp.body()) {
            if (resp.statusCode() != 200) {
                throw new IOException("Ollama HTTP " + resp.statusCode() + ": " + lines.collect(Collectors.joining()));
            }
            var it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line.isBlank()) continue;
                JsonNode n = om.readTree(line);
                String t = n.path("message").path("content").asText("");
                if (!t.isEmpty()) onToken.accept(t);
                if (n.path("done").asBoolean(false)) break;
            }
        }
    }

    /** 文本向量化（结果已 L2 归一化，点积即余弦相似度）。需先 `ollama pull bge-m3`。 */
    public List<float[]> embed(List<String> inputs) throws IOException, InterruptedException {
        ObjectNode b = om.createObjectNode();
        b.put("model", embedModel);
        b.put("keep_alive", "30m");
        ArrayNode in = b.putArray("input");
        inputs.forEach(in::add);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/embed"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(b)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("Ollama embed HTTP " + resp.statusCode() + ": " + resp.body());
        JsonNode arr = om.readTree(resp.body()).path("embeddings");
        if (!arr.isArray() || arr.size() != inputs.size()) throw new IOException("embedding 返回数量与输入不符");
        List<float[]> out = new ArrayList<>();
        for (JsonNode row : arr) {
            float[] v = new float[row.size()];
            double norm = 0;
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) row.get(i).asDouble();
                norm += (double) v[i] * v[i];
            }
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
            out.add(v);
        }
        return out;
    }

    private HttpRequest request(List<Msg> messages, boolean stream, boolean json) throws JsonProcessingException {
        ObjectNode b = om.createObjectNode();
        b.put("model", model);
        b.put("stream", stream);
        b.put("keep_alive", "30m");
        if (json) b.put("format", "json");
        ArrayNode ms = b.putArray("messages");
        for (Msg m : messages) ms.addObject().put("role", m.role()).put("content", m.content());
        ObjectNode o = b.putObject("options");
        o.put("temperature", json ? 0.1 : temperature);
        o.put("num_ctx", numCtx);
        return HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(b)))
                .build();
    }
}
