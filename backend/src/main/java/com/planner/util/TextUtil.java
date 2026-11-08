package com.planner.util;

import java.util.HashSet;
import java.util.Set;

public final class TextUtil {
    private TextUtil() {}

    /** 中文友好的 2-gram 集合（去掉标点/空白）。 */
    public static Set<String> bigrams(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        String t = s.toLowerCase().replaceAll("[^\\p{L}\\p{N}]", "");
        if (t.length() < 2) {
            if (!t.isEmpty()) out.add(t);
            return out;
        }
        for (int i = 0; i < t.length() - 1; i++) out.add(t.substring(i, i + 2));
        return out;
    }

    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int inter = 0;
        for (String x : a) if (b.contains(x)) inter++;
        return (double) inter / (a.size() + b.size() - inter);
    }
}
