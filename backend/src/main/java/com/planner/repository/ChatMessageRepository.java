package com.planner.repository;

import com.planner.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop6ByUserIdAndSessionIdOrderByIdDesc(Long userId, String sessionId);
    List<ChatMessage> findTop50ByUserIdAndSessionIdOrderByIdDesc(Long userId, String sessionId);

    /** 需在事务内调用 */
    void deleteByUserIdAndSessionId(Long userId, String sessionId);
}
