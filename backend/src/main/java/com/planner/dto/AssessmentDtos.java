package com.planner.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public final class AssessmentDtos {
    private AssessmentDtos() {}

    /** 提交测评：{answers: {题目id: "单值" | ["多值"] | 数字}} */
    public record SubmitReq(@NotNull(message = "answers 不能为空") Map<String, Object> answers) {}
}
