package com.planner.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 任务消费者：
 *  - concurrency 个 worker 线程轮询 Stream（不使用 BLOCK，避免占用共享的 Redis 连接）；这也是同时调用本地模型的最大并发
 *  - 处理成功 → XACK + XDEL
 *  - 处理失败 → 未超过 max-attempts：放入延迟 ZSET，(retry-backoff-seconds × 已尝试次数) 秒后重试；超过：进死信队列 + 回调 onDead
 *  - worker 崩溃/重启：启动时先重读自己名下未确认的消息；其余未确认且超过 reclaim-idle-seconds 的消息由巡检线程重新入队（算一次尝试）
 */
@Slf4j
@Component
public class AiTaskWorker {
    private final StringRedisTemplate redis;
    private final AiTaskQueue queue;
    private final ObjectMapper om;
    private final Map<String, AiTaskHandler> handlers = new HashMap<>();
    private final int concurrency;
    private final int maxAttempts;
    private final int backoffSeconds;
    private final int reclaimIdleSeconds;

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final String consumerBase = "w-" + hostName();
    private final AtomicInteger threadSeq = new AtomicInteger();
    private volatile boolean running;
    private volatile boolean groupReady;
    private ExecutorService workers;
    private ScheduledExecutorService housekeeper;
    private int tick;

    public AiTaskWorker(StringRedisTemplate redis, AiTaskQueue queue, ObjectMapper om, List<AiTaskHandler> list,
                        @Value("${app.ai-queue.concurrency:1}") int concurrency,
                        @Value("${app.ai-queue.max-attempts:3}") int maxAttempts,
                        @Value("${app.ai-queue.retry-backoff-seconds:30}") int backoffSeconds,
                        @Value("${app.ai-queue.reclaim-idle-seconds:300}") int reclaimIdleSeconds) {
        this.redis = redis;
        this.queue = queue;
        this.om = om;
        this.concurrency = Math.max(1, concurrency);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffSeconds = backoffSeconds;
        this.reclaimIdleSeconds = reclaimIdleSeconds;
        for (AiTaskHandler h : list) handlers.put(h.type(), h);
    }

    @PostConstruct
    void start() {
        running = true;
        workers = Executors.newFixedThreadPool(concurrency, r -> daemon(r, "ai-worker"));
        for (int i = 0; i < concurrency; i++) {
            String consumer = consumerBase + "-" + i;
            workers.submit(() -> workLoop(consumer));
        }
        housekeeper = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "ai-housekeeper"));
        housekeeper.scheduleWithFixedDelay(this::housekeeping, 2, 2, TimeUnit.SECONDS);
        log.info("AI 任务队列已启动：并发={} 最大尝试={} 处理器={}", concurrency, maxAttempts, handlers.keySet());
    }

    @PreDestroy
    void stop() {
        running = false;
        if (workers != null) workers.shutdownNow();          // 中断进行中的模型调用；未确认的消息会在下次启动时重新处理
        if (housekeeper != null) housekeeper.shutdownNow();
    }

    // ---------------- 消费 ----------------

    private void workLoop(String consumer) {
        int drainBudget = 20;     // 启动时先处理自己名下遗留的未确认消息
        boolean draining = true;
        while (running) {
            try {
                if (!groupReady) ensureGroup();
                ReadOffset offset = draining ? ReadOffset.from("0") : ReadOffset.lastConsumed();
                List<MapRecord<String, String, String>> recs = queue.ops().read(
                        Consumer.from(AiTaskQueue.GROUP, consumer),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(AiTaskQueue.STREAM, offset));
                if (recs == null || recs.isEmpty()) {
                    if (draining) { draining = false; continue; }
                    Thread.sleep(1000);
                    continue;
                }
                if (draining && --drainBudget <= 0) draining = false;
                for (MapRecord<String, String, String> r : recs) process(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("AI 队列消费异常，3 秒后重试: {}", e.toString());
                groupReady = false;
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void ensureGroup() {
        try {
            queue.ops().createGroup(AiTaskQueue.STREAM, ReadOffset.from("0-0"), AiTaskQueue.GROUP);
        } catch (RuntimeException e) {
            boolean busy = String.valueOf(e.getMessage()).contains("BUSYGROUP")
                    || (e.getCause() != null && String.valueOf(e.getCause().getMessage()).contains("BUSYGROUP"));
            if (!busy) throw e;
        }
        groupReady = true;
    }

    void process(MapRecord<String, String, String> rec) {
        RecordId rid = rec.getId();
        String id = rid.getValue();
        Map<String, String> f = rec.getValue();
        if (f == null || f.get("type") == null) {      // 数据已被删除的残留确认项
            safeAck(rid);
            return;
        }
        AiTaskHandler h = handlers.get(f.get("type"));
        if (h == null) {
            try {
                queue.deadLetter(f, "未知任务类型: " + f.get("type"));
            } catch (Exception e) {
                log.error("写入死信队列失败: {}", e.toString());
                return;
            }
            safeAck(rid);
            return;
        }
        inFlight.add(id);
        try {
            h.handle(om.readTree(f.get("payload")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;                                     // 不确认：重启后由启动重读或巡检重新投递
        } catch (Exception e) {
            fail(h, f, e, rid);
            return;
        } finally {
            inFlight.remove(id);
        }
        safeAck(rid);
    }

    private void fail(AiTaskHandler h, Map<String, String> f, Exception e, RecordId rid) {
        int attempt = parseInt(f.get("attempt"), 1);
        log.warn("AI 任务失败 type={} 第{}/{}次: {}", f.get("type"), attempt, maxAttempts, e.toString());
        try {
            if (attempt >= maxAttempts) {
                queue.deadLetter(f, e.toString());
                notifyDead(h, f, e);
            } else {
                queue.retryLater(f.get("type"), f.get("payload"), attempt + 1, backoffSeconds * 1000L * attempt);
            }
        } catch (Exception ex) {
            log.error("AI 任务失败后的处理出错，保留为未确认，等待巡检回收: {}", ex.toString());
            return;
        }
        safeAck(rid);
    }

    private void notifyDead(AiTaskHandler h, Map<String, String> f, Exception cause) {
        try {
            h.onDead(om.readTree(f.get("payload")), cause);
        } catch (Exception e) {
            log.warn("onDead 回调出错 type={}: {}", f.get("type"), e.toString());
        }
    }

    private void safeAck(RecordId rid) {
        try {
            queue.ops().acknowledge(AiTaskQueue.STREAM, AiTaskQueue.GROUP, rid);
            queue.ops().delete(AiTaskQueue.STREAM, rid);      // 先确认再删除，避免残留指向已删数据的未确认项
        } catch (Exception e) {
            log.warn("确认 AI 任务失败 id={}: {}（会被巡检回收后重新执行，处理器是幂等的）", rid.getValue(), e.toString());
        }
    }

    // ---------------- 巡检：搬运到期重试 + 回收崩溃遗留 ----------------

    private void housekeeping() {
        try {
            queue.moveDue();
            if (++tick % 15 == 0) reclaimStale();      // 约每 30 秒
        } catch (Exception e) {
            log.debug("AI 队列巡检异常: {}", e.toString());
        }
    }

    void reclaimStale() {
        PendingMessages pending = queue.ops().pending(AiTaskQueue.STREAM, AiTaskQueue.GROUP, Range.unbounded(), 100);
        if (pending == null || pending.isEmpty()) return;
        for (PendingMessage m : pending) {
            String id = m.getIdAsString();
            if (inFlight.contains(id)) continue;
            if (m.getElapsedTimeSinceLastDelivery().getSeconds() < reclaimIdleSeconds) continue;
            List<MapRecord<String, String, String>> found = queue.ops().range(AiTaskQueue.STREAM, Range.just(id));
            if (found == null || found.isEmpty() || found.get(0).getValue() == null || found.get(0).getValue().isEmpty()) {
                safeAck(m.getId());
                continue;
            }
            Map<String, String> f = found.get(0).getValue();
            int attempt = parseInt(f.get("attempt"), 1);
            try {
                if (attempt >= maxAttempts) {
                    queue.deadLetter(f, "worker 崩溃/超时，重试次数用尽");
                    AiTaskHandler h = handlers.get(f.get("type"));
                    if (h != null) notifyDead(h, f, new IllegalStateException("worker crashed"));
                } else {
                    queue.add(f.get("type"), f.get("payload"), attempt + 1);
                }
            } catch (Exception e) {
                log.warn("回收未确认的 AI 任务失败 id={}: {}", id, e.toString());
                continue;
            }
            log.warn("回收未确认的 AI 任务 id={} type={}", id, f.get("type"));
            safeAck(m.getId());
        }
    }

    // ---------------- 工具 ----------------

    private Thread daemon(Runnable r, String prefix) {
        Thread t = new Thread(r, prefix + "-" + threadSeq.incrementAndGet());
        t.setDaemon(true);
        return t;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "local";
        }
    }
}
