package com.planner.queue;

import com.fasterxml.jackson.databind.JsonNode;

/** AI 后台任务处理器。handle 抛出任何异常都会触发重试；任务会被至少投递一次，所以实现必须幂等。 */
public interface AiTaskHandler {
    /** 处理的任务类型，见 AiTaskTypes */
    String type();

    void handle(JsonNode payload) throws Exception;

    /** 重试次数用尽、进入死信队列后调用（做兜底处理，如把状态标记为失败）。不应抛异常。 */
    default void onDead(JsonNode payload, Exception cause) {}
}
