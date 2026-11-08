package com.planner.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表 assessment_record：每次测评一条，原始答案 + 当次画像快照 + AI 摘要。最新画像见 UserProfile。 */
@Getter
@Setter
public class AssessmentRecord {
    private Long id;
    private Long userId;
    private String answersJson;
    private String stage;
    private String mbti;
    private String profilePath;
    private String profileJson;
    private String summary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
