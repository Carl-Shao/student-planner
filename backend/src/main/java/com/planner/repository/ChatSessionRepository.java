package com.planner.repository;

import com.planner.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findByUserIdAndSessionId(Long userId, String sessionId);

    List<ChatSession> findTop50ByUserIdOrderByLastMessageAtDesc(Long userId);
}
