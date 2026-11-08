package com.planner.mapper;

import com.planner.entity.ChatSession;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionMapper {
    ChatSession selectByUserAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /** 最近的会话，按最后消息时间倒序 */
    List<ChatSession> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    int insert(ChatSession session);

    /** 又完成了一轮问答：消息数 +2，刷新最后消息时间 */
    int touch(@Param("id") Long id, @Param("now") LocalDateTime now);

    int updateTitle(@Param("id") Long id, @Param("title") String title, @Param("now") LocalDateTime now);

    int deleteById(@Param("id") Long id);
}
