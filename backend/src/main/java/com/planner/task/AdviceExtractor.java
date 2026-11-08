package com.planner.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planner.client.OllamaClient;
import com.planner.dto.AdviceIn;
import com.planner.dto.Msg;
import com.planner.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 对话结束后异步提取"值得长期保留的建议"并写入个人画像（不阻塞对话）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdviceExtractor {
    private static final Set<String> CATEGORIES = Set.of("学习方法", "学习计划", "辅导班", "其他");

    private final OllamaClient ollama;
    private final ProfileService profiles;
    private final ObjectMapper om;

    @Async("aiExecutor")
    public void extractAndMerge(long uid, String sessionId, Long messageId, String userMsg, String reply) {
        if (!profiles.exists(uid)) return;   // 还没测评，无画像可写
        try {
            String prompt = """
                    下面是一段教育规划对话。请只从【助手回答】中提取最值得长期保留的建议要点，用于更新学生的个人画像。
                    要求：
                    1. 只提取回答里明确出现的建议，不要自己新增或改写含义。
                    2. 最多5条，每条不超过60字，具体、可执行。
                    3. category 只能是："学习方法"、"学习计划"、"辅导班"、"其他" 四选一。
                    4. 没有可提取的建议时返回 {"advices":[]}。
                    5. 只输出 JSON，格式：{"advices":[{"category":"学习方法","content":"..."}]}

                    【用户问题】
                    %s

                    【助手回答】
                    %s""".formatted(userMsg, reply);
            String out = ollama.chat(List.of(new Msg("user", prompt)), true);
            JsonNode arr = om.readTree(out).path("advices");
            List<AdviceIn> items = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String cat = n.path("category").asText("其他");
                    String content = n.path("content").asText("").trim();
                    if (content.length() < 6 || content.length() > 120) continue;   // 过滤明显异常的输出
                    if (!CATEGORIES.contains(cat)) cat = "其他";
                    items.add(new AdviceIn(cat, content));
                    if (items.size() >= 5) break;
                }
            }
            profiles.addAdvices(uid, sessionId, messageId, items);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("建议提取失败 uid={}: {}", uid, e.toString());
        }
    }
}
