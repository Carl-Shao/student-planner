package com.planner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planner.dto.AdviceIn;
import com.planner.entity.AdviceRecord;
import com.planner.entity.UserProfile;
import com.planner.repository.AdviceRecordRepository;
import com.planner.repository.AssessmentRecordRepository;
import com.planner.repository.UserProfileRepository;
import com.planner.util.TextUtil;
import com.planner.util.TxUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 个人画像（数据库为准）：
 *  - user_profile：最新画像（常用字段拆列 + 完整 JSON，不含建议）
 *  - advice_record：对话提取的建议
 *  - assessment_record：每次测评的历史快照
 * 画像文件 {profile-dir}/{userId}/profile.json 是"导出文件"：每次数据变化提交后自动重新导出（含建议），供下载/外部使用。
 */
@Slf4j
@Service
public class ProfileService {
    public static final int MAX_ADVICES = 30;
    public static final String ACTIVE = "ACTIVE";
    public static final String DELETED = "DELETED";
    public static final String ARCHIVED = "ARCHIVED";

    private final ObjectMapper om;
    private final UserProfileRepository profiles;
    private final AdviceRecordRepository advices;
    private final AssessmentRecordRepository records;
    private final Path root;
    private final ConcurrentHashMap<Long, Object> fileLocks = new ConcurrentHashMap<>();

    public ProfileService(ObjectMapper om, UserProfileRepository profiles, AdviceRecordRepository advices,
                          AssessmentRecordRepository records, @Value("${app.profile-dir}") String dir) {
        this.om = om;
        this.profiles = profiles;
        this.advices = advices;
        this.records = records;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public Path pathOf(long uid) {
        return root.resolve(String.valueOf(uid)).resolve("profile.json");
    }

    // ---------- 读 ----------

    public boolean exists(long uid) {
        return profiles.existsByUserId(uid);
    }

    /** 结构化画像（不含建议）；不存在返回 null */
    @Transactional(readOnly = true)
    public ObjectNode load(long uid) {
        return profiles.findByUserId(uid).map(p -> parse(p.getProfileJson())).orElse(null);
    }

    /** 完整视图 = 画像 + advices[]（ACTIVE，新的在前）。画像页、对话提示词、导出文件都用它。 */
    @Transactional(readOnly = true)
    public ObjectNode view(long uid) {
        ObjectNode p = load(uid);
        if (p == null) return null;
        ArrayNode arr = p.putArray("advices");
        for (AdviceRecord a : advices.findByUserIdAndStatusOrderByIdDesc(uid, ACTIVE)) {
            ObjectNode n = arr.addObject();
            n.put("id", a.getId());
            n.put("category", a.getCategory());
            n.put("content", a.getContent());
            n.put("stage", a.getStage());
            n.put("sessionId", a.getSessionId());
            n.put("createdAt", String.valueOf(a.getCreatedAt()));
        }
        return p;
    }

    // ---------- 写 ----------

    /** 测评提交后：写入/覆盖最新画像，并把常用字段提取到列上。 */
    @Transactional
    public void saveLatest(long uid, long assessmentId, ObjectNode p) {
        UserProfile up = profiles.lockByUserId(uid).orElseGet(() -> {
            UserProfile n = new UserProfile();
            n.setUserId(uid);
            return n;
        });
        up.setAssessmentId(assessmentId);
        up.setNickname(p.path("nickname").asText(""));
        up.setAge(p.path("age").asInt());
        up.setGender(p.path("gender").asText(""));
        up.setGrade(p.path("grade").asText(""));
        up.setCity(p.path("city").asText(""));
        up.setStage(p.path("stage").asText(""));
        up.setMbti(p.at("/mbti/type").asText(""));
        up.setHollandCode(p.at("/holland/code").asText(""));
        up.setCompleteness(p.at("/completeness/percent").asInt());
        up.setSummary(p.path("summary").asText(""));
        up.setSummaryStatus(p.path("summaryStatus").asText("PENDING"));
        up.setProfileJson(write(p));
        profiles.save(up);
        TxUtil.afterCommit(() -> exportFile(uid));
    }

    /** 异步摘要生成完毕：写入对应测评记录；若该测评仍是最新画像，同步更新 user_profile。 */
    @Transactional
    public void updateSummary(long uid, long assessmentId, String summary, String status) {
        records.findById(assessmentId).filter(r -> r.getUserId().equals(uid)).ifPresent(r -> r.setSummary(summary));
        profiles.lockByUserId(uid).filter(p -> Objects.equals(p.getAssessmentId(), assessmentId)).ifPresent(p -> {
            ObjectNode n = parse(p.getProfileJson());
            n.put("summary", summary);
            n.put("summaryStatus", status);
            n.put("updatedAt", LocalDateTime.now().toString());
            p.setSummary(summary);
            p.setSummaryStatus(status);
            p.setProfileJson(write(n));
        });
        TxUtil.afterCommit(() -> exportFile(uid));
    }

    /** 并入对话提取的建议：与最近 100 条（含已删除的）按 2-gram Jaccard>=0.6 去重；ACTIVE 最多保留 MAX_ADVICES 条。 */
    @Transactional
    public int addAdvices(long uid, String sessionId, Long messageId, List<AdviceIn> items) {
        if (items.isEmpty()) return 0;
        UserProfile up = profiles.lockByUserId(uid).orElse(null);   // 同时作为按用户串行化的锁
        if (up == null) return 0;

        List<Set<String>> seen = new ArrayList<>();
        for (AdviceRecord a : advices.findTop100ByUserIdOrderByIdDesc(uid)) seen.add(TextUtil.bigrams(a.getContent()));

        int added = 0;
        for (AdviceIn it : items) {
            Set<String> g = TextUtil.bigrams(it.content());
            boolean dup = seen.stream().anyMatch(s -> TextUtil.jaccard(s, g) >= 0.6);
            if (dup) continue;
            seen.add(g);
            AdviceRecord a = new AdviceRecord();
            a.setUserId(uid);
            a.setSessionId(sessionId);
            a.setMessageId(messageId);
            a.setCategory(it.category());
            a.setContent(it.content());
            a.setStage(up.getStage());
            advices.save(a);
            added++;
        }

        List<AdviceRecord> active = advices.findByUserIdAndStatusOrderByIdAsc(uid, ACTIVE);
        for (int i = 0; i < active.size() - MAX_ADVICES; i++) active.get(i).setStatus(ARCHIVED);

        if (added > 0) TxUtil.afterCommit(() -> exportFile(uid));
        return added;
    }

    /** 用户删除建议（软删除，保留用于去重）。 */
    @Transactional
    public boolean deleteAdvice(long uid, long adviceId) {
        Optional<AdviceRecord> a = advices.findByIdAndUserId(adviceId, uid).filter(x -> ACTIVE.equals(x.getStatus()));
        if (a.isEmpty()) return false;
        a.get().setStatus(DELETED);
        TxUtil.afterCommit(() -> exportFile(uid));
        return true;
    }

    // ---------- 导出画像文件 ----------

    private void exportFile(long uid) {
        try {
            ObjectNode v = view(uid);
            if (v == null) return;
            synchronized (fileLocks.computeIfAbsent(uid, k -> new Object())) {
                Path target = pathOf(uid);
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling("profile.json.tmp");
                om.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), v);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            log.warn("画像文件导出失败 uid={}: {}", uid, e.toString());
        }
    }

    private ObjectNode parse(String json) {
        try {
            return (ObjectNode) om.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String write(JsonNode n) {
        try {
            return om.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- 给大模型看的画像文本 ----------

    public String toPromptText(ObjectNode p, boolean forChat) {
        StringBuilder sb = new StringBuilder();
        sb.append("称呼：").append(t(p, "/nickname")).append("；年龄：").append(p.path("age").asText("?"))
          .append("岁；性别：").append(t(p, "/gender")).append("；城市：").append(t(p, "/city")).append('\n');
        sb.append("当前阶段：").append(t(p, "/stageLabel")).append("（").append(t(p, "/grade"))
          .append("）；填写人：").append(t(p, "/filledBy")).append("；家庭教育预算：").append(t(p, "/budget")).append('\n');
        sb.append("MBTI倾向：").append(t(p, "/mbti/type")).append("（").append(t(p, "/mbti/note"));
        JsonNode border = p.at("/mbti/borderline");
        if (border.isArray() && !border.isEmpty()) sb.append("；倾向不明显的维度：").append(join(border));
        sb.append("）\n");
        sb.append("上过的班：").append(join(p.at("/tutoring/classes"))).append("；情况：").append(t(p, "/tutoring/detail"))
          .append("；态度：").append(t(p, "/tutoring/attitude")).append('\n');
        sb.append("成绩：").append(t(p, "/academic/scores")).append("；擅长：").append(join(p.at("/academic/strong")))
          .append("；薄弱：").append(join(p.at("/academic/weak"))).append("；每日课外学习：").append(t(p, "/academic/dailyHours"))
          .append("；学习困扰：").append(join(p.at("/academic/issues"))).append('\n');
        sb.append("兴趣：").append(join(p.path("interests"))).append("；性格自述：").append(t(p, "/traits")).append('\n');
        sb.append("学习习惯：风格=").append(join(p.at("/habits/style"))).append("；单次专注=").append(t(p, "/habits/focus"))
          .append("；最佳时段=").append(t(p, "/habits/bestTime")).append("；睡眠=").append(t(p, "/habits/sleep"))
          .append("；每周运动=").append(t(p, "/habits/exercise")).append("；每日屏幕娱乐=").append(t(p, "/habits/screen"))
          .append("；家长参与=").append(t(p, "/habits/family")).append('\n');
        String hol = p.at("/holland/code").asText("");
        if (!hol.isBlank()) {
            sb.append("职业兴趣（霍兰德简化版）：").append(hol).append("（").append(join(p.at("/holland/labels"))).append("）\n");
        }
        sb.append("规则分析出的关注点：").append(join(p.at("/highlights/concerns"))).append('\n');
        sb.append("目标：").append(t(p, "/goals/goal")).append("；意向职业：").append(t(p, "/goals/career"))
          .append("；意向路径：").append(t(p, "/goals/path")).append("；最困扰：").append(t(p, "/goals/worry")).append('\n');
        if (forChat) {
            String summary = p.path("summary").asText("");
            if (!summary.isBlank()) sb.append("画像摘要：").append(summary).append('\n');
            JsonNode adv = p.path("advices");
            if (adv.isArray() && !adv.isEmpty()) {
                sb.append("此前已给过的建议（避免重复，可在此基础上深化）：\n");
                for (int i = 0; i < Math.min(5, adv.size()); i++) {   // advices 已按时间倒序
                    sb.append("- [").append(adv.get(i).path("category").asText()).append("] ")
                      .append(adv.get(i).path("content").asText()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    public String fallbackSummary(ObjectNode p) {
        return "%s，%s岁，处于%s阶段。MBTI倾向为%s（仅供参考）。擅长：%s；薄弱：%s。主要目标：%s。".formatted(
                t(p, "/nickname"), p.path("age").asText("?"), t(p, "/stageLabel"), t(p, "/mbti/type"),
                join(p.at("/academic/strong")), join(p.at("/academic/weak")), t(p, "/goals/goal"));
    }

    private static String t(JsonNode n, String ptr) {
        String v = n.at(ptr).asText("");
        return v.isBlank() ? "未填写" : v;
    }

    private static String join(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) return "无";
        StringJoiner sj = new StringJoiner("、");
        arr.forEach(x -> sj.add(x.asText()));
        return sj.toString();
    }
}
