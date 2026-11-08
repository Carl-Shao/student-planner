package com.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planner.common.BizException;
import com.planner.domain.ProfileInsights;
import com.planner.domain.QuestionBank;
import com.planner.domain.QuestionBank.Question;
import com.planner.domain.StageUtil;
import com.planner.entity.AssessmentRecord;
import com.planner.mapper.AssessmentRecordMapper;
import com.planner.mapper.UserMapper;
import com.planner.queue.AiTaskQueue;
import com.planner.queue.AiTaskTypes;
import com.planner.util.TxUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AssessmentService {
    private static final String[] DIMS = {"EI", "SN", "TF", "JP"};

    private final ObjectMapper om;
    private final ProfileService profiles;
    private final AiTaskQueue aiTasks;
    private final UserMapper users;
    private final AssessmentRecordMapper records;
    private final StringRedisTemplate redis;

    @Transactional
    public Map<String, Object> submit(long uid, Map<String, Object> raw) {
        String lockKey = "assess:lock:" + uid;   // 防止重复点击并发提交
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10)))) {
            throw new BizException(429, "正在处理上一次提交，请稍后再试");
        }
        try {
            Map<String, List<String>> a = normalize(raw);
            ObjectNode profile = buildProfile(uid, a);

            AssessmentRecord rec = new AssessmentRecord();
            rec.setUserId(uid);
            rec.setStage(profile.path("stage").asText());
            rec.setMbti(profile.at("/mbti/type").asText(""));
            rec.setProfilePath(profiles.pathOf(uid).toString());
            try {
                rec.setAnswersJson(om.writeValueAsString(a));
                rec.setProfileJson(om.writeValueAsString(profile));   // 当次画像快照
            } catch (JsonProcessingException e) {
                throw new BizException(400, "答案格式错误");
            }
            LocalDateTime now = LocalDateTime.now();
            rec.setCreatedAt(now);
            rec.setUpdatedAt(now);
            records.insert(rec);
            users.markAssessmentDone(uid);

            profiles.saveLatest(uid, rec.getId(), profile);   // 最新画像入 user_profile，提交后自动导出画像文件
            long recId = rec.getId();
            TxUtil.afterCommit(() -> {   // 事务提交后再入队，保证 worker 读到已提交的数据
                boolean queued = aiTasks.enqueue(AiTaskTypes.PROFILE_SUMMARY, Map.of("uid", uid, "assessmentId", recId));
                if (!queued) profiles.markSummaryFailed(uid, recId);   // Redis 不可用：直接用模板摘要，避免一直 PENDING
            });

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("assessmentId", recId);
            out.put("stage", profile.path("stage").asText());
            out.put("stageLabel", profile.path("stageLabel").asText());
            out.put("mbti", profile.path("mbti").path("type").asText());
            out.put("summaryStatus", "PENDING");
            return out;
        } catch (RuntimeException e) {
            redis.delete(lockKey);   // 校验失败/出错时允许立即重新提交
            throw e;
        }
    }

    public Map<String, Object> status(long uid) {
        Map<String, Object> m = new LinkedHashMap<>();
        AssessmentRecord last = records.selectLatestByUserId(uid);
        m.put("done", last != null);
        m.put("lastAt", last == null ? null : String.valueOf(last.getCreatedAt()));
        return m;
    }

    // ---------- 校验与规范化 ----------

    private Map<String, List<String>> normalize(Map<String, Object> raw) {
        Map<String, List<String>> out = new HashMap<>();
        for (Question q : QuestionBank.all()) {
            Object v = raw.get(q.id());
            List<String> vals = new ArrayList<>();
            if (v instanceof Collection<?> c) {
                for (Object o : c) if (o != null) vals.add(o.toString().trim());
            } else if (v != null) {
                vals.add(v.toString().trim());
            }
            vals.removeIf(String::isEmpty);
            if (vals.isEmpty()) {
                if (q.required()) throw new BizException(400, "请回答：" + q.text());
                continue;
            }
            switch (q.type()) {
                case "SINGLE", "SCALE" -> {
                    if (vals.size() != 1 || !validOption(q, vals.get(0))) throw bad(q);
                }
                case "MULTI" -> {
                    if (vals.size() > 13) throw bad(q);
                    for (String x : vals) if (!validOption(q, x)) throw bad(q);
                }
                case "NUMBER" -> {
                    int n;
                    try {
                        n = (int) Double.parseDouble(vals.get(0));
                    } catch (NumberFormatException e) {
                        throw bad(q);
                    }
                    if (n < 3 || n > 60) throw new BizException(400, "年龄需在3-60之间");
                    out.put(q.id(), List.of(String.valueOf(n)));
                    continue;
                }
                default -> {
                    if (vals.get(0).length() > 500) throw new BizException(400, "「" + q.text() + "」内容过长（最多500字）");
                }
            }
            out.put(q.id(), vals);
        }
        return out;
    }

    private static boolean validOption(Question q, String v) {
        return q.options().stream().anyMatch(o -> o.value().equals(v));
    }

    private static BizException bad(Question q) {
        return new BizException(400, "「" + q.text() + "」的答案不合法");
    }

    // ---------- 生成画像 ----------

    private static String one(Map<String, List<String>> a, String id) {
        List<String> l = a.get(id);
        return l == null || l.isEmpty() ? "" : l.get(0);
    }

    private static List<String> many(Map<String, List<String>> a, String id) {
        return a.getOrDefault(id, List.of());
    }

    private ArrayNode arr(List<String> l) {
        ArrayNode n = om.createArrayNode();
        l.forEach(n::add);
        return n;
    }

    private ObjectNode buildProfile(long uid, Map<String, List<String>> a) {
        int age = Integer.parseInt(one(a, "b_age"));
        String grade = one(a, "b_grade");
        StageUtil.Stage st = StageUtil.of(age, grade);

        ObjectNode p = om.createObjectNode();
        p.put("userId", uid);
        p.put("nickname", one(a, "b_name"));
        p.put("age", age);
        p.put("gender", one(a, "b_gender"));
        p.put("city", one(a, "b_city"));
        p.put("grade", grade);
        p.put("filledBy", one(a, "b_filled_by"));
        p.put("budget", one(a, "b_budget"));
        p.put("stage", st.code());
        p.put("stageLabel", st.label());

        ObjectNode m = p.putObject("mbti");
        ObjectNode scores = m.putObject("scores");
        ArrayNode border = m.putArray("borderline");
        StringBuilder type = new StringBuilder();
        for (String d : DIMS) {
            String c0 = String.valueOf(d.charAt(0)), c1 = String.valueOf(d.charAt(1));
            int n0 = 0, n1 = 0;
            for (int i = 1; i <= 4; i++) {
                String v = one(a, "mbti_" + d + "_" + i);
                if (v.equals(c0)) n0++; else if (v.equals(c1)) n1++;
            }
            scores.put(c0, n0);
            scores.put(c1, n1);
            if (n0 == n1) border.add(d);
            type.append(n0 >= n1 ? c0 : c1);
        }
        m.put("type", type.toString());
        m.put("note", age < 12 ? "年龄较小，性格仍在发展中，MBTI仅供参考" : "简化版自评，非正式MBTI结果，仅供参考");

        ObjectNode t = p.putObject("tutoring");
        t.set("classes", arr(many(a, "t_classes")));
        t.put("detail", one(a, "t_detail"));
        t.put("attitude", one(a, "t_attitude"));

        ObjectNode ac = p.putObject("academic");
        ac.put("scores", one(a, "a_scores"));
        ac.set("strong", arr(many(a, "a_strong")));
        ac.set("weak", arr(many(a, "a_weak")));
        ac.put("dailyHours", one(a, "a_hours"));
        ac.set("issues", arr(many(a, "a_issues")));

        p.set("interests", arr(many(a, "i_interests")));
        p.put("traits", one(a, "i_traits"));

        ObjectNode g = p.putObject("goals");
        g.put("goal", one(a, "g_goal"));
        g.put("career", one(a, "g_career"));
        g.put("path", one(a, "g_path"));
        g.put("worry", one(a, "g_worry"));

        ObjectNode hb = p.putObject("habits");
        hb.set("style", arr(many(a, "h_style")));
        hb.put("focus", one(a, "h_focus"));
        hb.put("bestTime", one(a, "h_best_time"));
        hb.put("sleep", one(a, "h_sleep"));
        hb.put("exercise", one(a, "h_exercise"));
        hb.put("screen", one(a, "h_screen"));
        hb.put("family", one(a, "h_family"));
        ProfileInsights.apply(p, a, om);   // 霍兰德代码、优势/关注点、完整度

        p.put("summary", "");
        p.put("summaryStatus", "PENDING");
        String now = LocalDateTime.now().toString();
        p.put("createdAt", now);
        p.put("updatedAt", now);
        return p;
    }
}
