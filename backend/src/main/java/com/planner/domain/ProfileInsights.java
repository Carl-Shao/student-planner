package com.planner.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * 由测评答案"规则计算"出的画像字段（不依赖大模型，结果稳定可解释）：
 *  - holland：霍兰德职业兴趣代码（12 题全答才生成）
 *  - highlights：strengths 优势 / concerns 关注点
 *  - completeness：画像完整度百分比 + 缺失项（引导用户补充）
 */
public final class ProfileInsights {
    private ProfileInsights() {}

    private static final String HOL = "RIASEC";
    private static final Map<Character, String> HOL_LABEL = Map.of(
            'R', "实用型", 'I', "研究型", 'A', "艺术型", 'S', "社会型", 'E', "企业型", 'C', "常规型");

    private static final List<String> TRACKED = List.of(
            "b_gender", "b_city", "b_budget", "t_classes", "t_detail", "t_attitude",
            "a_scores", "a_strong", "a_weak", "a_hours", "a_issues",
            "h_style", "h_focus", "h_best_time", "h_sleep", "h_exercise", "h_screen", "h_family",
            "i_interests", "i_traits", "g_goal", "g_career", "g_path", "g_worry");

    public static void apply(ObjectNode p, Map<String, List<String>> a, ObjectMapper om) {
        holland(p, a, om);
        highlights(p, a, om);
        completeness(p, a, om);
    }

    private static void holland(ObjectNode p, Map<String, List<String>> a, ObjectMapper om) {
        int[] sc = new int[6];
        for (int d = 0; d < 6; d++) {
            for (int i = 1; i <= 2; i++) {
                String v = one(a, "hol_" + HOL.charAt(d) + "_" + i);
                if (v.isEmpty()) return;            // 没答全就不生成
                sc[d] += Integer.parseInt(v);
            }
        }
        Integer[] idx = {0, 1, 2, 3, 4, 5};
        Arrays.sort(idx, (x, y) -> sc[y] != sc[x] ? Integer.compare(sc[y], sc[x]) : Integer.compare(x, y));
        ObjectNode h = p.putObject("holland");
        ObjectNode scores = h.putObject("scores");
        for (int d = 0; d < 6; d++) scores.put(String.valueOf(HOL.charAt(d)), sc[d]);
        StringBuilder code = new StringBuilder();
        ArrayNode labels = h.putArray("labels");
        for (int i = 0; i < 3; i++) {
            char c = HOL.charAt(idx[i]);
            code.append(c);
            labels.add(HOL_LABEL.get(c));
        }
        h.put("code", code.toString());
        h.put("note", "简化版兴趣自评，仅供职业探索参考");
    }

    private static void highlights(ObjectNode p, Map<String, List<String>> a, ObjectMapper om) {
        int age = p.path("age").asInt(0);
        List<String> st = new ArrayList<>();
        List<String> cn = new ArrayList<>();

        List<String> strong = without(many(a, "a_strong"), "无/不适用");
        if (!strong.isEmpty()) st.add("擅长科目：" + String.join("、", strong));
        List<String> interests = many(a, "i_interests");
        if (!interests.isEmpty()) st.add("兴趣：" + String.join("、", interests));
        if (p.has("holland")) {
            st.add("职业兴趣倾向：" + p.at("/holland/code").asText() + "（" + joinArr(p.at("/holland/labels")) + "）");
        }

        String sleep = one(a, "h_sleep"), exercise = one(a, "h_exercise"), focus = one(a, "h_focus");
        String screen = one(a, "h_screen"), attitude = one(a, "t_attitude"), family = one(a, "h_family");
        if (sleep.equals("8-9小时") || sleep.equals("9小时以上") || (age > 12 && sleep.equals("7-8小时"))) st.add("睡眠基本充足");
        if (exercise.equals("3-4次") || exercise.equals("5次以上")) st.add("有较好的运动习惯");
        if (focus.equals("1小时以上")) st.add("专注力较好");

        List<String> weak = without(many(a, "a_weak"), "无/不适用");
        if (!weak.isEmpty()) cn.add("薄弱科目：" + String.join("、", weak));
        List<String> issues = without(many(a, "a_issues"), "暂无明显困扰");
        if (!issues.isEmpty()) cn.add("学习困扰：" + String.join("、", issues));
        if (sleep.equals("不足7小时")) cn.add("睡眠不足");
        else if (age <= 12 && sleep.equals("7-8小时")) cn.add("睡眠可能偏少（儿童通常需要更长睡眠）");
        if (screen.equals("4小时以上")) cn.add("屏幕娱乐时间偏长");
        if (exercise.equals("几乎没有")) cn.add("缺少运动");
        if (focus.equals("不到15分钟") && age >= 8) cn.add("单次专注时间较短");
        if (attitude.equals("抗拒/压力大")) cn.add("对课外班有抗拒或压力");
        if (family.equals("压力较大/期望很高")) cn.add("来自家庭的期望压力较大");

        ObjectNode h = p.putObject("highlights");
        h.set("strengths", arr(st, om));
        h.set("concerns", arr(cn.size() > 6 ? cn.subList(0, 6) : cn, om));
    }

    private static void completeness(ObjectNode p, Map<String, List<String>> a, ObjectMapper om) {
        int answered = 0;
        List<String> missing = new ArrayList<>();
        for (String id : TRACKED) {
            if (a.containsKey(id)) answered++;
            else missing.add(QuestionBank.get(id).text());
        }
        if (p.has("holland")) answered++;
        else missing.add("职业兴趣（霍兰德）测评");
        int total = TRACKED.size() + 1;
        ObjectNode c = p.putObject("completeness");
        c.put("percent", answered * 100 / total);
        c.set("missing", arr(missing.size() > 8 ? missing.subList(0, 8) : missing, om));
    }

    private static String one(Map<String, List<String>> a, String id) {
        List<String> l = a.get(id);
        return l == null || l.isEmpty() ? "" : l.get(0);
    }

    private static List<String> many(Map<String, List<String>> a, String id) {
        return a.getOrDefault(id, List.of());
    }

    private static List<String> without(List<String> l, String drop) {
        List<String> r = new ArrayList<>(l);
        r.remove(drop);
        return r;
    }

    private static String joinArr(com.fasterxml.jackson.databind.JsonNode arr) {
        StringJoiner sj = new StringJoiner("、");
        arr.forEach(x -> sj.add(x.asText()));
        return sj.toString();
    }

    private static ArrayNode arr(List<String> l, ObjectMapper om) {
        ArrayNode n = om.createArrayNode();
        l.forEach(n::add);
        return n;
    }
}
