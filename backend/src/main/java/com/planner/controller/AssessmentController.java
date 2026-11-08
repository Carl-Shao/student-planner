package com.planner.controller;

import com.planner.common.Result;
import com.planner.common.UserContext;
import com.planner.domain.QuestionBank;
import com.planner.dto.AssessmentDtos.SubmitReq;
import com.planner.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {
    private final AssessmentService service;

    /** 题目：{sections:[{code,title}], questions:[{id,section,type,text,hint,required,options}]} */
    @GetMapping("/questions")
    public Result<Map<String, Object>> questions() {
        List<Map<String, String>> sections = new ArrayList<>();
        QuestionBank.SECTIONS.forEach((k, v) -> sections.add(Map.of("code", k, "title", v)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sections", sections);
        out.put("questions", QuestionBank.all());
        return Result.ok(out);
    }

    /** 提交答案：{answers: {题目id: "单值" | ["多值"] | 数字}}；可重复提交（重新测评，保留已有建议） */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@Valid @RequestBody SubmitReq req) {
        return Result.ok(service.submit(UserContext.require(), req.answers()));
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(service.status(UserContext.require()));
    }
}
