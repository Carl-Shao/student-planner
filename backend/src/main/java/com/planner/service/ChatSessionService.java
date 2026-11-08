package com.planner.service;

import com.planner.common.BizException;
import com.planner.dto.ChatDtos.MsgView;
import com.planner.dto.ChatDtos.SessionView;
import com.planner.entity.ChatMessage;
import com.planner.entity.ChatSession;
import com.planner.repository.ChatMessageRepository;
import com.planner.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 会话管理：会话列表、重命名、删除、历史消息，以及一轮问答的原子落库。 */
@Service
@RequiredArgsConstructor
public class ChatSessionService {
    private static final Pattern SID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;

    /** 没传 sessionId 则新建一个；传了则校验格式。 */
    public String resolve(String sid) {
        if (sid == null || sid.isBlank()) return UUID.randomUUID().toString();
        requireValid(sid);
        return sid;
    }

    public static void requireValid(String sid) {
        if (sid == null || !SID.matcher(sid).matches()) throw new BizException(400, "sessionId 格式不正确");
    }

    /** 保存一轮问答（用户消息 + 助手回复）并更新会话；返回助手消息 id。 */
    @Transactional
    public Long saveExchange(long uid, String sid, String userMsg, String reply) {
        ChatSession s = sessions.findByUserIdAndSessionId(uid, sid).orElseGet(() -> {
            ChatSession n = new ChatSession();
            n.setUserId(uid);
            n.setSessionId(sid);
            n.setTitle(titleOf(userMsg));
            return n;
        });

        ChatMessage u = new ChatMessage();
        u.setUserId(uid);
        u.setSessionId(sid);
        u.setRole("user");
        u.setContent(userMsg);
        messages.save(u);

        ChatMessage a = new ChatMessage();
        a.setUserId(uid);
        a.setSessionId(sid);
        a.setRole("assistant");
        a.setContent(reply);
        messages.save(a);

        s.setMessageCount(s.getMessageCount() + 2);
        s.setLastMessageAt(LocalDateTime.now());
        sessions.save(s);
        return a.getId();
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(long uid) {
        return sessions.findTop50ByUserIdOrderByLastMessageAtDesc(uid).stream()
                .map(s -> new SessionView(s.getSessionId(), s.getTitle(), s.getMessageCount(), s.getLastMessageAt(), s.getCreatedAt()))
                .toList();
    }

    /** 某个会话最近 50 条消息（按时间正序） */
    @Transactional(readOnly = true)
    public List<MsgView> history(long uid, String sid) {
        requireValid(sid);
        List<ChatMessage> l = new ArrayList<>(messages.findTop50ByUserIdAndSessionIdOrderByIdDesc(uid, sid));
        Collections.reverse(l);
        return l.stream().map(m -> new MsgView(m.getRole(), m.getContent(), m.getCreatedAt())).toList();
    }

    @Transactional
    public void rename(long uid, String sid, String title) {
        requireValid(sid);
        ChatSession s = sessions.findByUserIdAndSessionId(uid, sid).orElseThrow(() -> new BizException(404, "会话不存在"));
        s.setTitle(title.strip());
    }

    /** 删除会话及其全部消息（已从中提取的建议保留在画像里，可在画像页单独删除）。 */
    @Transactional
    public void delete(long uid, String sid) {
        requireValid(sid);
        ChatSession s = sessions.findByUserIdAndSessionId(uid, sid).orElseThrow(() -> new BizException(404, "会话不存在"));
        messages.deleteByUserIdAndSessionId(uid, sid);
        sessions.delete(s);
    }

    private static String titleOf(String msg) {
        String t = msg.strip().replaceAll("\\s+", " ");
        return t.length() > 30 ? t.substring(0, 30) + "…" : t;
    }
}
