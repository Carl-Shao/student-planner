package com.planner.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 用户最新画像（每个用户一行）。常用字段拆成列方便查询/统计，完整画像 JSON 在 profileJson（不含建议，建议见 AdviceRecord）。 */
@Getter
@Setter
@Entity
@Table(name = "user_profile", uniqueConstraints = @UniqueConstraint(name = "uk_up_user", columnNames = "user_id"))
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 生成该画像的测评记录 id（用于丢弃过期的异步摘要结果） */
    @Column(name = "assessment_id")
    private Long assessmentId;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Column(name = "age")
    private Integer age;

    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "grade", length = 64)
    private String grade;

    @Column(name = "city", length = 64)
    private String city;

    @Column(name = "stage", length = 32)
    private String stage;

    @Column(name = "mbti", length = 16)
    private String mbti;

    @Column(name = "holland_code", length = 8)
    private String hollandCode;

    @Column(name = "completeness")
    private Integer completeness;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** PENDING / DONE / FAILED */
    @Column(name = "summary_status", length = 16)
    private String summaryStatus;

    @Column(name = "profile_json", columnDefinition = "MEDIUMTEXT")
    private String profileJson;

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
