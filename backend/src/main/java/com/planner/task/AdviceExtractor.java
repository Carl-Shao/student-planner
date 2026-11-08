package com.planner.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planner.client.OllamaClient;
import com.planner.dto.AdviceIn;
import com.planner.dto.Msg;
import com.planner.queue.AiTaskHandler;
import com.planner.queue.AiTaskTypes;
import com.planner.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 建议提取任务：从一轮对话的助手回答里提取值得长期保留的建议。幂等（addAdvices 会去重）；用尽重试后丢弃（建议属于锦上添花）。 */
@Component
@RequiredArgsConstructor
public class AdviceExtractor implements AiTaskHandler {
    private static final Set<String> CATEGORIES = Set.of("学习方法", "学习计划", "辅导班", "其他");

    private final OllamaClient ollama;
    private final ProfileService profiles;
    private final ObjectMapper om;

    @Override
    public String type() {
        return AiTaskTypes.ADVICE_EXTRACT;
    }

    @Override
    public void handle(JsonNode in) throws Exception {
        long uid = in.path("uid").asLong();
        String sessionId = in.path("sessionId").asText(null);
        Long messageId = in.hasNonNull("messageId") ? in.get("messageId").asLong() : null;
        String userMsg = in.path("userMsg").asText("");
        String reply = in.path("reply").asText("");
        if (!profiles.exists(uid)) return;   // 还没测评，无画像可写

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
        JsonNode arr = om.readTree(out).path("advices");   // 模型输出不是合法 JSON 时抛异常 → 触发重试
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
    }
}
