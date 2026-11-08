package com.planner.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表 user_profile：用户最新画像（每个用户一行）。常用字段拆成列便于查询，完整画像 JSON 在 profileJson（不含建议）。 */
@Getter
@Setter
public class UserProfile {
    private Long id;
    private Long userId;
    /** 生成该画像的测评记录 id（用于丢弃过期的异步摘要结果） */
    private Long assessmentId;
    private String nickname;
    private Integer age;
    private String gender;
    private String grade;
    private String city;
    private String stage;
    private String mbti;
    private String hollandCode;
    private Integer completeness;
    private String summary;
    /** PENDING / DONE / FAILED */
    private String summaryStatus;
    private String profileJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
