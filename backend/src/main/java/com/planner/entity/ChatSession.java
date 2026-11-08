package com.planner.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表 chat_session：会话列表，(user_id, session_id) 唯一。 */
@Getter
@Setter
public class ChatSession {
    private Long id;
    private Long userId;
    private String sessionId;
    private String title;
    private int messageCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
