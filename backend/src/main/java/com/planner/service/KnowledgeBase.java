package com.planner.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planner.client.OllamaClient;
import com.planner.util.TextUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识库：knowledge/{stage}.md，按 "## 标题" 切段；stage = kindergarten/primary/middle/high/university/career/general。
 * 检索：优先用 Ollama embedding（bge-m3）做向量检索；embedding 未启用/模型未就绪/调用失败时自动回退为关键词(2-gram)检索。
 * 向量在启动时后台生成，并缓存到磁盘（按 模型+内容 的哈希），内容没变就不会重复计算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeBase {
    public record Chunk(String stage, String title, String text, Set<String> grams, Set<String> titleGrams,
                        AtomicReference<float[]> vec) {}

    private final OllamaClient ollama;
    private final ObjectMapper om;
    private final List<Chunk> chunks = new ArrayList<>();   // 启动时一次性加载，之后只读

    @Value("${app.knowledge-dir:}") private String externalDir;
    @Value("${app.knowledge.embedding.enabled:false}") private boolean embEnabled;
    @Value("${app.knowledge.embedding.model:bge-m3}") private String embModel;
    @Value("${app.knowledge.embedding.min-score:0.4}") private double minScore;
    @Value("${app.knowledge.embedding.cache-file:./data/knowledge-cache/embeddings.json}") private String cacheFile;

    private volatile boolean embReady = false;
    private volatile long lastEmbAttempt = 0;
    private final AtomicBoolean embRunning = new AtomicBoolean(false);

    @PostConstruct
    void load() {
        try {
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:knowledge/*.md")) {
                try (InputStream in = r.getInputStream()) {
                    parse(stem(r.getFilename()), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            if (externalDir != null && !externalDir.isBlank() && Files.isDirectory(Path.of(externalDir))) {
                try (var s = Files.list(Path.of(externalDir))) {
                    for (Path f : s.filter(x -> x.toString().endsWith(".md")).toList()) {
                        parse(stem(f.getFileName().toString()), Files.readString(f));
                    }
                }
            }
        } catch (IOException e) {
            log.error("知识库加载失败", e);
        }
        log.info("知识库已加载 {} 段，向量检索：{}", chunks.size(), embEnabled ? "启用（后台构建中）" : "未启用");
        if (embEnabled) startEmbedding();
    }

    private static String stem(String name) {
        return name == null ? "general" : name.replaceAll("\\.md$", "");
    }

    private void parse(String stage, String content) {
        String title = null;
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\\R")) {
            if (line.startsWith("## ")) {
                flush(stage, title, sb);
                title = line.substring(3).trim();
                sb.setLength(0);
            } else if (!line.startsWith("# ")) {
                sb.append(line).append('\n');
            }
        }
        flush(stage, title, sb);
    }

    private void flush(String stage, String title, StringBuilder sb) {
        if (title == null || sb.toString().isBlank()) return;
        String text = sb.toString().trim();
        chunks.add(new Chunk(stage, title, text, TextUtil.bigrams(title + text), TextUtil.bigrams(title),
                new AtomicReference<>()));
    }

    // ---------------- 向量构建 ----------------

    private void maybeRetry() {
        if (System.currentTimeMillis() - lastEmbAttempt > 60_000) startEmbedding();   // Ollama 还没起来时，每分钟最多重试一次
    }

    private void startEmbedding() {
        if (!embRunning.compareAndSet(false, true)) return;
        lastEmbAttempt = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                buildEmbeddings();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("知识库向量构建失败，暂用关键词检索（请确认已执行 ollama pull {}）: {}", embModel, e.toString());
            } finally {
                embRunning.set(false);
            }
        });
    }

    private void buildEmbeddings() throws IOException, InterruptedException {
        if (chunks.isEmpty()) return;
        Map<String, float[]> cache = readCache();
        List<Chunk> todo = new ArrayList<>();
        for (Chunk c : chunks) {
            float[] v = cache.get(key(c));
            if (v != null) c.vec().set(v); else todo.add(c);
        }
        for (int i = 0; i < todo.size(); i += 8) {
            List<Chunk> batch = todo.subList(i, Math.min(i + 8, todo.size()));
            List<float[]> vs = ollama.embed(batch.stream().map(c -> c.title() + "\n" + c.text()).toList());
            for (int j = 0; j < batch.size(); j++) batch.get(j).vec().set(vs.get(j));
        }
        if (!todo.isEmpty()) writeCache();
        embReady = chunks.stream().allMatch(c -> c.vec().get() != null);
        log.info("知识库向量就绪={}（新计算 {} 段，缓存命中 {} 段）", embReady, todo.size(), chunks.size() - todo.size());
    }

    private String key(Chunk c) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((embModel + "\n" + c.title() + "\n" + c.text()).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, float[]> readCache() {
        Path p = Path.of(cacheFile);
        if (!Files.exists(p)) return new HashMap<>();
        try {
            return om.readValue(p.toFile(), new TypeReference<Map<String, float[]>>() {});
        } catch (IOException e) {
            log.warn("向量缓存读取失败，将重新计算: {}", e.toString());
            return new HashMap<>();
        }
    }

    private void writeCache() {
        Map<String, float[]> fresh = new HashMap<>();   // 只保存当前内容对应的向量，自动清理过期项
        for (Chunk c : chunks) if (c.vec().get() != null) fresh.put(key(c), c.vec().get());
        try {
            Path p = Path.of(cacheFile);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            om.writeValue(p.toFile(), fresh);
        } catch (IOException e) {
            log.warn("向量缓存写入失败: {}", e.toString());
        }
    }

    // ---------------- 检索 ----------------

    /** stage 为 null 时在所有阶段中检索；总会包含 general 段。 */
    public List<Chunk> search(String stage, String query, int k) {
        List<Chunk> pool = chunks.stream()
                .filter(c -> stage == null || c.stage().equals(stage) || c.stage().equals("general"))
                .toList();
        if (embEnabled && !embReady) maybeRetry();
        if (embReady) {
            try {
                float[] qv = ollama.embed(List.of(query)).get(0);
                List<Map.Entry<Chunk, Double>> scored = new ArrayList<>();
                for (Chunk c : pool) scored.add(Map.entry(c, dot(qv, c.vec().get())));
                scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                if (log.isDebugEnabled() && !scored.isEmpty()) {
                    log.debug("知识库向量检索 top1={} score={}", scored.get(0).getKey().title(), scored.get(0).getValue());
                }
                List<Chunk> hit = scored.stream().filter(e -> e.getValue() >= minScore).limit(k)
                        .map(Map.Entry::getKey).toList();
                return withOverview(hit, pool, stage);
            } catch (IOException e) {
                log.warn("查询向量化失败，回退关键词检索: {}", e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return withOverview(keywordSearch(pool, query, k), pool, stage);
    }

    private static List<Chunk> withOverview(List<Chunk> hit, List<Chunk> pool, String stage) {
        if (!hit.isEmpty() || stage == null) return hit;
        return pool.stream().filter(c -> c.stage().equals(stage)).limit(2).toList();   // 无命中时给本阶段概览
    }

    private static List<Chunk> keywordSearch(List<Chunk> pool, String query, int k) {
        Set<String> q = TextUtil.bigrams(query);
        return pool.stream()
                .map(c -> Map.entry(c, score(q, c)))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static long score(Set<String> q, Chunk c) {
        long s = 0;
        for (String g : q) {
            if (c.grams().contains(g)) s++;
            if (c.titleGrams().contains(g)) s++;
        }
        return s;
    }

    private static double dot(float[] a, float[] b) {
        if (b == null || a.length != b.length) return 0;
        double s = 0;
        for (int i = 0; i < a.length; i++) s += (double) a[i] * b[i];
        return s;
    }
}
