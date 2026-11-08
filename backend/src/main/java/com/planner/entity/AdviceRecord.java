package com.planner.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 从对话中提取的建议。status：ACTIVE 展示中 / DELETED 用户删除（保留用于去重，避免再次提取） / ARCHIVED 超过上限被挤出。 */
@Getter
@Setter
@Entity
@Table(name = "advice_record", indexes = @Index(name = "idx_advice_user_status", columnList = "user_id,status"))
public class AdviceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 来源的助手消息 id（chat_message.id），便于追溯 */
    @Column(name = "message_id")
    private Long messageId;

    /** 学习方法 / 学习计划 / 辅导班 / 其他 */
    @Column(name = "category", length = 16)
    private String category;

    @Column(name = "content", length = 255)
    private String content;

    @Column(name = "stage", length = 32)
    private String stage;

    @Column(name = "source", length = 16)
    private String source = "chat";

    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
