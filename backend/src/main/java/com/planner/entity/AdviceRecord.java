package com.planner.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 表 advice_record：从对话提取的建议。status：ACTIVE 展示中 / DELETED 用户删除（保留用于去重） / ARCHIVED 超过上限被挤出。 */
@Getter
@Setter
public class AdviceRecord {
    private Long id;
    private Long userId;
    private String sessionId;
    /** 来源的助手消息 id（chat_message.id） */
    private Long messageId;
    /** 学习方法 / 学习计划 / 辅导班 / 其他 */
    private String category;
    private String content;
    private String stage;
    private String source = "chat";
    private String status = "ACTIVE";
    private LocalDateTime createdAt;
}
