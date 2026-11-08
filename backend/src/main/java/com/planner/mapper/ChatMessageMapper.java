package com.planner.mapper;

import com.planner.entity.ChatMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ChatMessageMapper {
    int insert(ChatMessage message);

    /** 最近 limit 条，新的在前 */
    List<ChatMessage> selectRecent(@Param("userId") Long userId, @Param("sessionId") String sessionId,
                                   @Param("limit") int limit);

    int deleteBySession(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
