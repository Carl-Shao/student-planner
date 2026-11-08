package com.planner.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 每次测评一条：原始答案 + 当次画像快照 + AI 摘要。最新画像见 UserProfile。 */
@Getter
@Setter
@Entity
@Table(name = "assessment_record", indexes = @Index(name = "idx_ar_user", columnList = "user_id"))
public class AssessmentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "answers_json", columnDefinition = "MEDIUMTEXT")
    private String answersJson;

    @Column(name = "stage", length = 32)
    private String stage;

    @Column(name = "mbti", length = 16)
    private String mbti;

    @Column(name = "profile_path", length = 255)
    private String profilePath;

    @Column(name = "profile_json", columnDefinition = "MEDIUMTEXT")
    private String profileJson;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
