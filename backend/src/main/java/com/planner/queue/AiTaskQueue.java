package com.planner.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 基于 Redis Stream 的 AI 任务队列（生产者侧）。
 *  - ai:tasks          待处理（Stream + 消费者组 ai-workers）
 *  - ai:tasks:delayed  等待重试（ZSET，score=到期毫秒时间）
 *  - ai:tasks:dead     死信（Stream，保留最近 1000 条，可用 XRANGE ai:tasks:dead - + 查看）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskQueue {
    public static final String STREAM = "ai:tasks";
    public static final String GROUP = "ai-workers";
    public static final String DELAYED = "ai:tasks:delayed";
    public static final String DEAD = "ai:tasks:dead";

    private final StringRedisTemplate redis;
    private final ObjectMapper om;

    StreamOperations<String, String, String> ops() {
        return redis.opsForStream();
    }

    /** 入队。Redis 不可用时只记日志并返回 false，绝不抛异常（不能影响主业务流程）。 */
    public boolean enqueue(String type, Map<String, Object> payload) {
        try {
            add(type, om.writeValueAsString(payload), 1);
            return true;
        } catch (Exception e) {
            log.warn("AI 任务入队失败 type={}: {}", type, e.toString());
            return false;
        }
    }

    void add(String type, String payloadJson, int attempt) {
        Map<String, String> f = new HashMap<>();
        f.put("type", type);
        f.put("payload", payloadJson);
        f.put("attempt", String.valueOf(attempt));
        ops().add(StreamRecords.newRecord().in(STREAM).ofMap(f));
    }

    /** 失败后延迟重试：先放进延迟 ZSET，到期后由 worker 的搬运线程放回 Stream。 */
    void retryLater(String type, String payloadJson, int nextAttempt, long delayMs) throws JsonProcessingException {
        ObjectNode n = om.createObjectNode();
        n.put("type", type);
        n.put("payload", payloadJson);
        n.put("attempt", nextAttempt);
        n.put("nonce", UUID.randomUUID().toString());   // 保证相同任务的 ZSET 成员不会互相覆盖
        redis.opsForZSet().add(DELAYED, om.writeValueAsString(n), System.currentTimeMillis() + delayMs);
    }

    /** 把到期的重试任务搬回 Stream；ZREM 返回 1 的实例才负责搬运，多实例也不会重复。 */
    int moveDue() {
        Set<String> due = redis.opsForZSet().rangeByScore(DELAYED, 0, System.currentTimeMillis(), 0, 20);
        if (due == null || due.isEmpty()) return 0;
        int moved = 0;
        for (String member : due) {
            Long removed = redis.opsForZSet().remove(DELAYED, member);
            if (removed == null || removed == 0) continue;
            try {
                JsonNode j = om.readTree(member);
                add(j.path("type").asText(), j.path("payload").asText(), j.path("attempt").asInt(1));
                moved++;
            } catch (Exception e) {
                log.warn("搬运重试任务失败，5 秒后重试: {}", e.toString());
                try {
                    redis.opsForZSet().add(DELAYED, member, System.currentTimeMillis() + 5000);
                } catch (Exception ignore) { /* Redis 仍不可用，只能丢弃这一次 */ }
            }
        }
        return moved;
    }

    void deadLetter(Map<String, String> fields, String error) {
        Map<String, String> f = new HashMap<>(fields);
        f.put("error", error == null ? "" : error.substring(0, Math.min(error.length(), 500)));
        f.put("failedAt", Instant.now().toString());
        ops().add(StreamRecords.newRecord().in(DEAD).ofMap(f));
        ops().trim(DEAD, 1000);
    }
}
