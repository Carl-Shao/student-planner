package com.planner.domain;

import java.util.regex.Pattern;

public final class StageUtil {
    private StageUtil() {}

    public record Stage(String code, String label) {}

    private static final Pattern CAREER = Pattern.compile("工作|就业|在职|职场|待业|上班");
    private static final Pattern UNI = Pattern.compile("大学|大[一二三四五]|研究生|硕士|博士|研[一二三]|专科|本科");
    private static final Pattern HIGH = Pattern.compile("高中|高[一二三]");
    private static final Pattern MIDDLE = Pattern.compile("初中|初[一二三]");
    private static final Pattern PRIMARY = Pattern.compile("小学|[一二三四五六]年级");
    private static final Pattern KG = Pattern.compile("幼儿园|幼儿|大班|中班|小班|学前");

    /** 优先按用户填写的年级判断，其次按年龄。 */
    public static Stage of(int age, String grade) {
        String g = grade == null ? "" : grade;
        if (CAREER.matcher(g).find()) return career();
        if (UNI.matcher(g).find()) return new Stage("university", "大学");
        if (HIGH.matcher(g).find()) return new Stage("high", "高中");
        if (MIDDLE.matcher(g).find()) return new Stage("middle", "初中");
        if (PRIMARY.matcher(g).find()) return new Stage("primary", "小学");
        if (KG.matcher(g).find()) return new Stage("kindergarten", "幼儿园");
        if (age <= 5) return new Stage("kindergarten", "幼儿园");
        if (age <= 11) return new Stage("primary", "小学");
        if (age <= 14) return new Stage("middle", "初中");
        if (age <= 17) return new Stage("high", "高中");
        if (age <= 22) return new Stage("university", "大学");
        return career();
    }

    private static Stage career() { return new Stage("career", "职场/就业"); }
}
