package com.planner.domain;

import com.planner.domain.QuestionBank.Question;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuestionBankTest {
    @Test
    void idsAreUniqueAndSectionsExist() {
        Set<String> ids = new HashSet<>();
        for (Question q : QuestionBank.all()) {
            assertTrue(ids.add(q.id()), "重复的题目 id: " + q.id());
            assertTrue(QuestionBank.SECTIONS.containsKey(q.section()), "未知分组: " + q.section());
        }
    }

    @Test
    void choiceQuestionsHaveOptions() {
        for (Question q : QuestionBank.all()) {
            boolean choice = q.type().equals("SINGLE") || q.type().equals("MULTI") || q.type().equals("SCALE");
            if (choice) assertFalse(q.options().isEmpty(), q.id() + " 缺少选项");
            else assertTrue(q.options().isEmpty(), q.id() + " 不应有选项");
        }
    }

    @Test
    void mbtiAndHollandCounts() {
        long mbti = QuestionBank.all().stream().filter(q -> q.section().equals("MBTI")).count();
        long holland = QuestionBank.all().stream().filter(q -> q.section().equals("HOLLAND")).count();
        assertEquals(16, mbti);
        assertEquals(12, holland);
        QuestionBank.all().stream().filter(q -> q.section().equals("MBTI")).forEach(q -> assertTrue(q.required()));
    }
}
