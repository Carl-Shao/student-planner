package com.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatReq(
            @NotBlank(message = "消息不能为空") @Size(max = 1000, message = "消息最长1000字") String message,
            @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$", message = "sessionId 格式不正确") String sessionId) {}

    public record ChatResp(String sessionId, String reply) {}

    public record MsgView(String role, String content, LocalDateTime createdAt) {}

    public record SessionView(String sessionId, String title, int messageCount,
                              LocalDateTime lastMessageAt, LocalDateTime createdAt) {}

    public record RenameReq(@NotBlank(message = "标题不能为空") @Size(max = 100, message = "标题最长100字") String title) {}
}
