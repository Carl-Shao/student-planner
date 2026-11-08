package com.planner.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfileInsightsTest {
    private final ObjectMapper om = new ObjectMapper();

    private static void hol(Map<String, List<String>> a, String dim, String v) {
        a.put("hol_" + dim + "_1", List.of(v));
        a.put("hol_" + dim + "_2", List.of(v));
    }

    private static boolean contains(JsonNode arr, String text) {
        for (JsonNode n : arr) if (n.asText().contains(text)) return true;
        return false;
    }

    @Test
    void hollandCodeIsTopThreeDimensions() {
        ObjectNode p = om.createObjectNode();
        p.put("age", 15);
        Map<String, List<String>> a = new HashMap<>();
        hol(a, "R", "5"); hol(a, "I", "4"); hol(a, "E", "3");
        hol(a, "S", "2"); hol(a, "A", "1"); hol(a, "C", "1");
        ProfileInsights.apply(p, a, om);
        assertEquals("RIE", p.at("/holland/code").asText());
        assertEquals(10, p.at("/holland/scores/R").asInt());
    }

    @Test
    void hollandSkippedWhenIncomplete() {
        ObjectNode p = om.createObjectNode();
        p.put("age", 15);
        Map<String, List<String>> a = new HashMap<>();
        a.put("hol_R_1", List.of("5"));
        ProfileInsights.apply(p, a, om);
        assertFalse(p.has("holland"));
    }

    @Test
    void highlightsAndCompleteness() {
        ObjectNode p = om.createObjectNode();
        p.put("age", 15);
        Map<String, List<String>> a = new HashMap<>();
        a.put("a_strong", List.of("数学", "物理"));
        a.put("a_weak", List.of("英语"));
        a.put("h_sleep", List.of("不足7小时"));
        a.put("h_exercise", List.of("几乎没有"));
        hol(a, "R", "5"); hol(a, "I", "4"); hol(a, "E", "3");
        hol(a, "S", "2"); hol(a, "A", "1"); hol(a, "C", "1");
        ProfileInsights.apply(p, a, om);

        assertTrue(contains(p.at("/highlights/strengths"), "数学"));
        assertTrue(contains(p.at("/highlights/concerns"), "薄弱科目：英语"));
        assertTrue(contains(p.at("/highlights/concerns"), "睡眠不足"));
        assertTrue(contains(p.at("/highlights/concerns"), "缺少运动"));
        assertEquals(20, p.at("/completeness/percent").asInt());   // 25 项中完成 5 项（含霍兰德）
        assertTrue(p.at("/completeness/missing").size() <= 8);
    }

    @Test
    void childSleepThresholdIsStricter() {
        ObjectNode child = om.createObjectNode();
        child.put("age", 9);
        Map<String, List<String>> a = new HashMap<>();
        a.put("h_sleep", List.of("7-8小时"));
        ProfileInsights.apply(child, a, om);
        assertTrue(contains(child.at("/highlights/concerns"), "睡眠"));

        ObjectNode teen = om.createObjectNode();
        teen.put("age", 16);
        ProfileInsights.apply(teen, a, om);
        assertFalse(contains(teen.at("/highlights/concerns"), "睡眠"));
        assertTrue(contains(teen.at("/highlights/strengths"), "睡眠基本充足"));
    }
}
