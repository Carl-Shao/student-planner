package com.planner.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表 chat_message */
@Getter
@Setter
public class ChatMessage {
    private Long id;
    private Long userId;
    private String sessionId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
