package com.planner.queue;

public final class AiTaskTypes {
    private AiTaskTypes() {}

    /** 测评提交后生成 AI 画像摘要。payload: {uid, assessmentId} */
    public static final String PROFILE_SUMMARY = "PROFILE_SUMMARY";
    /** 对话结束后提取建议写入 advice_record。payload: {uid, sessionId, messageId, userMsg, reply} */
    public static final String ADVICE_EXTRACT = "ADVICE_EXTRACT";
}
