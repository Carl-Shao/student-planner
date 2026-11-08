package com.planner.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class AiTaskWorkerTest {
    private static final RecordId ID = RecordId.of("1-0");
    private static final String PAYLOAD = "{\"uid\":1}";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AiTaskQueue queue = mock(AiTaskQueue.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, String, String> ops = mock(StreamOperations.class);
    private final AiTaskHandler handler = mock(AiTaskHandler.class);
    private AiTaskWorker worker;

    @BeforeEach
    void setUp() {
        when(handler.type()).thenReturn("T");
        when(queue.ops()).thenReturn(ops);
        // 并发 1，最多 3 次，退避 30 秒，回收 300 秒
        worker = new AiTaskWorker(redis, queue, new ObjectMapper(), List.of(handler), 1, 3, 30, 300);
    }

    private MapRecord<String, String, String> rec(String type, int attempt) {
        return MapRecord.create(AiTaskQueue.STREAM,
                Map.of("type", type, "payload", PAYLOAD, "attempt", String.valueOf(attempt))).withId(ID);
    }

    private void verifyAcked() {
        verify(ops).acknowledge(AiTaskQueue.STREAM, AiTaskQueue.GROUP, ID);
        verify(ops).delete(AiTaskQueue.STREAM, ID);
    }

    @Test
    void successIsAcknowledged() throws Exception {
        worker.process(rec("T", 1));
        verify(handler).handle(any());
        verifyAcked();
        verify(queue, never()).retryLater(any(), any(), anyInt(), anyLong());
        verify(queue, never()).deadLetter(any(), any());
    }

    @Test
    void firstFailureIsRetriedAfterBackoff() throws Exception {
        doThrow(new IllegalStateException("boom")).when(handler).handle(any());
        worker.process(rec("T", 1));
        verify(queue).retryLater(eq("T"), eq(PAYLOAD), eq(2), eq(30_000L));
        verifyAcked();
        verify(queue, never()).deadLetter(any(), any());
    }

    @Test
    void backoffGrowsWithAttempts() throws Exception {
        doThrow(new IllegalStateException("boom")).when(handler).handle(any());
        worker.process(rec("T", 2));
        verify(queue).retryLater(eq("T"), eq(PAYLOAD), eq(3), eq(60_000L));
    }

    @Test
    void lastFailureGoesToDeadLetterAndCallsOnDead() throws Exception {
        doThrow(new IllegalStateException("boom")).when(handler).handle(any());
        worker.process(rec("T", 3));
        verify(queue).deadLetter(any(), contains("boom"));
        verify(handler).onDead(any(), any());
        verify(queue, never()).retryLater(any(), any(), anyInt(), anyLong());
        verifyAcked();
    }

    @Test
    void unknownTypeIsDeadLettered() throws Exception {
        worker.process(rec("X", 1));
        verify(queue).deadLetter(any(), anyString());
        verify(handler, never()).handle(any());
        verifyAcked();
    }

    @Test
    void failureToScheduleRetryLeavesMessagePending() throws Exception {
        doThrow(new IllegalStateException("boom")).when(handler).handle(any());
        doThrow(new IllegalStateException("redis down")).when(queue).retryLater(any(), any(), anyInt(), anyLong());
        worker.process(rec("T", 1));
        verify(ops, never()).acknowledge(AiTaskQueue.STREAM, AiTaskQueue.GROUP, ID);
    }
}
