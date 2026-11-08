package com.planner.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StageUtilTest {
    @Test
    void byAgeWhenGradeBlank() {
        assertEquals("kindergarten", StageUtil.of(4, "").code());
        assertEquals("primary", StageUtil.of(8, "").code());
        assertEquals("middle", StageUtil.of(13, null).code());
        assertEquals("high", StageUtil.of(16, "").code());
        assertEquals("university", StageUtil.of(19, "").code());
        assertEquals("career", StageUtil.of(23, "").code());
    }

    @Test
    void gradeTextOverridesAge() {
        assertEquals("high", StageUtil.of(18, "高三").code());
        assertEquals("university", StageUtil.of(17, "大一").code());
        assertEquals("middle", StageUtil.of(15, "初三").code());
        assertEquals("primary", StageUtil.of(6, "小学一年级").code());
        assertEquals("kindergarten", StageUtil.of(6, "幼儿园大班").code());
    }

    @Test
    void workKeywordWinsOverUniversity() {
        assertEquals("career", StageUtil.of(25, "大学毕业，已工作2年").code());
    }
}
