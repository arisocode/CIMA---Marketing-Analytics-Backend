package com.cimaxis.demo.integration.collab.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CollabProjectStreamConsumerTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private StreamOperations<String, Object, Object> streamOps;

    @Mock
    private ProjectProjectionService projectionService;

    private CollabProjectStreamConsumer consumer;
    private JsonMapper jsonMapper;

    private static final String STREAM = "stream:collab.events";
    private static final String DLQ = "stream:collab.events-dlq";
    private static final String GROUP = "group:marketing.collab-events";
    private static final String CONSUMER = "marketing-1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        when(redis.opsForStream()).thenReturn(streamOps);

        consumer = new CollabProjectStreamConsumer(
                redis, jsonMapper, projectionService,
                STREAM, DLQ, GROUP, CONSUMER, 50, 3
        );
        ReflectionTestUtils.setField(consumer, "consumerGroupReady", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgesMessageOnSuccessfulProjection() {
        RecordId recordId = RecordId.of("100-0");
        String payload = """
            {"id":"e-1","type":"project.created","data":{"projectId":"p-1"}}
            """;
        MapRecord<String, Object, Object> record = MapRecord.create(STREAM, Map.<Object, Object>of("payload", payload)).withId(recordId);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of()) // pending empty
                .thenReturn(List.of(record)); // new messages

        consumer.consume();

        verify(projectionService).apply(any());
        verify(streamOps).acknowledge(STREAM, GROUP, recordId);
        verify(streamOps, never()).add(eq(DLQ), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void leavesMessagePendingWhenErrorUnderMaxRetries() {
        RecordId recordId = RecordId.of("200-0");
        String payload = """
            {"id":"e-2","type":"project.created","data":{"projectId":"p-2"}}
            """;
        MapRecord<String, Object, Object> record = MapRecord.create(STREAM, Map.<Object, Object>of("payload", payload)).withId(recordId);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(record));

        doThrow(new RuntimeException("Transient DB issue")).when(projectionService).apply(any());

        PendingMessage pendingMsg = mock(PendingMessage.class);
        when(pendingMsg.getTotalDeliveryCount()).thenReturn(1L); // 1st attempt < 3
        PendingMessages pendingMessages = new PendingMessages(GROUP, List.of(pendingMsg));

        when(streamOps.pending(eq(STREAM), any(Consumer.class), any(Range.class), eq(1L)))
                .thenReturn(pendingMessages);

        consumer.consume();

        verify(streamOps, never()).acknowledge(STREAM, GROUP, recordId);
        verify(streamOps, never()).add(eq(DLQ), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesToDlqAndAcknowledgesWhenMaxRetriesExceeded() {
        RecordId recordId = RecordId.of("300-0");
        String payload = """
            {"id":"e-3","type":"project.created","data":{"projectId":"p-3"}}
            """;
        MapRecord<String, Object, Object> record = MapRecord.create(STREAM, Map.<Object, Object>of("payload", payload)).withId(recordId);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record)) // returned from pending
                .thenReturn(List.of());

        doThrow(new RuntimeException("Poison pill corrupted")).when(projectionService).apply(any());

        PendingMessage pendingMsg = mock(PendingMessage.class);
        when(pendingMsg.getTotalDeliveryCount()).thenReturn(3L); // 3rd attempt >= maxRetries
        PendingMessages pendingMessages = new PendingMessages(GROUP, List.of(pendingMsg));

        when(streamOps.pending(eq(STREAM), any(Consumer.class), any(Range.class), eq(1L)))
                .thenReturn(pendingMessages);

        consumer.consume();

        verify(streamOps).add(eq(DLQ), any(Map.class));
        verify(streamOps).acknowledge(STREAM, GROUP, recordId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimsOrphanedMessagesFromInactiveConsumers() {
        RecordId recordId = RecordId.of("400-0");
        String payload = """
            {"id":"e-4","type":"project.created","data":{"projectId":"p-4"}}
            """;
        MapRecord<String, Object, Object> record = MapRecord.create(
                STREAM, Map.<Object, Object>of("payload", payload)).withId(recordId);

        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of())
                .thenReturn(List.of());

        PendingMessage orphanedMsg = mock(PendingMessage.class);
        when(orphanedMsg.getId()).thenReturn(recordId);
        when(orphanedMsg.getConsumerName()).thenReturn("dead-pod-consumer");
        when(orphanedMsg.getElapsedTimeSinceLastDelivery()).thenReturn(java.time.Duration.ofMillis(35000L));
        PendingMessages pendingMessages = new PendingMessages(GROUP, List.of(orphanedMsg));

        when(streamOps.pending(eq(STREAM), eq(GROUP), any(Range.class), eq(50L)))
                .thenReturn(pendingMessages);

        when(streamOps.claim(eq(STREAM), eq(GROUP), eq(CONSUMER), any(java.time.Duration.class), eq(recordId)))
                .thenReturn(List.of(record));

        consumer.consume();

        verify(projectionService).apply(any());
        verify(streamOps).acknowledge(STREAM, GROUP, recordId);
    }
}
