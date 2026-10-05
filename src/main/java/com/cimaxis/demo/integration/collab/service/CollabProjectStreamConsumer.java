package com.cimaxis.demo.integration.collab.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor resiliente de eventos de Collab con reintentos PEL y DLQ (ADR-005).
 */
@Component
@ConditionalOnProperty(name = "cimaxis.collab-projection.enabled", havingValue = "true")
public class CollabProjectStreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(CollabProjectStreamConsumer.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final ProjectProjectionService projectionService;
    private final String stream;
    private final String dlqStream;
    private final String group;
    private final String consumer;
    private final int batchSize;
    private final int maxRetries;
    private volatile boolean consumerGroupReady;
    private static final long CLAIM_INTERVAL_MS = 30_000L;
    private static final long MIN_IDLE_CLAIM_MS = 30_000L;
    private long lastClaimTimeMs = 0L;

    public CollabProjectStreamConsumer(
            StringRedisTemplate redis,
            JsonMapper jsonMapper,
            ProjectProjectionService projectionService,
            @Value("${cimaxis.collab-projection.stream:stream:collab.events}") String stream,
            @Value("${cimaxis.collab-projection.dlq-stream:stream:collab.events-dlq}") String dlqStream,
            @Value("${cimaxis.collab-projection.consumer-group:group:marketing.collab-events}") String group,
            @Value("${cimaxis.collab-projection.consumer-name:${HOSTNAME:marketing}-projects}") String consumer,
            @Value("${cimaxis.collab-projection.batch-size:50}") int batchSize,
            @Value("${cimaxis.collab-projection.max-retries:3}") int maxRetries) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.projectionService = projectionService;
        this.stream = stream;
        this.dlqStream = dlqStream;
        this.group = group;
        this.consumer = consumer;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    @Scheduled(fixedDelayString = "${cimaxis.collab-projection.poll-delay-ms:1000}")
    public void consume() {
        try {
            ensureConsumerGroup();
            drainPendingMessages();
            claimOrphanedMessagesIfDue();
            readNewMessages();
        } catch (DataAccessException error) {
            consumerGroupReady = false;
            log.warn("El proyector no puede conectar a Redis; reintentara en siguiente ciclo: {}", error.getMessage());
        }
    }

    private void drainPendingMessages() {
        List<MapRecord<String, Object, Object>> pending = redis.opsForStream().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(batchSize),
                StreamOffset.create(stream, ReadOffset.from("0-0")));
        if (pending != null && !pending.isEmpty()) {
            for (MapRecord<String, Object, Object> message : pending) {
                processMessage(message);
            }
        }
    }

    private void claimOrphanedMessagesIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastClaimTimeMs < CLAIM_INTERVAL_MS) {
            return;
        }
        lastClaimTimeMs = now;
        claimOrphanedMessages();
    }

    private void claimOrphanedMessages() {
        try {
            PendingMessages pending = redis.opsForStream().pending(
                    stream, group, Range.unbounded(), batchSize);
            if (pending == null || pending.isEmpty()) return;

            for (var pm : pending) {
                if (pm.getElapsedTimeSinceLastDelivery().toMillis() >= MIN_IDLE_CLAIM_MS
                        && !consumer.equals(pm.getConsumerName())) {
                    List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(
                            stream, group, consumer, Duration.ofMillis(MIN_IDLE_CLAIM_MS), pm.getId());
                    if (claimed != null && !claimed.isEmpty()) {
                        log.info("Mensaje huerfano {} reclamado de consumidor inactivo {}",
                                pm.getId(), pm.getConsumerName());
                        for (MapRecord<String, Object, Object> message : claimed) {
                            processMessage(message);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No se pudieron reclamar mensajes huerfanos de la PEL: {}", e.getMessage());
        }
    }

    private void readNewMessages() {
        List<MapRecord<String, Object, Object>> messages = redis.opsForStream().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(batchSize),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
        if (messages != null && !messages.isEmpty()) {
            for (MapRecord<String, Object, Object> message : messages) {
                processMessage(message);
            }
        }
    }

    private void processMessage(MapRecord<String, Object, Object> message) {
        try {
            Object payloadObj = message.getValue().get("payload");
            if (payloadObj == null) {
                redis.opsForStream().acknowledge(stream, group, message.getId());
                return;
            }
            JsonNode event = jsonMapper.readTree(String.valueOf(payloadObj));
            String type = event.path("type").asText();
            if ("project.created".equals(type) || "project.updated".equals(type)) {
                projectionService.apply(event);
            }
            redis.opsForStream().acknowledge(stream, group, message.getId());
        } catch (Exception error) {
            handleFailure(message, error);
        }
    }

    private void handleFailure(MapRecord<String, Object, Object> message, Exception error) {
        long deliveryCount = getDeliveryCount(message.getId());
        if (deliveryCount >= maxRetries) {
            log.error("Evento Collab {} supero reintentos ({}/{}); desviando a DLQ {}",
                    message.getId(), deliveryCount, maxRetries, dlqStream);
            sendToDlq(message, deliveryCount, error);
            redis.opsForStream().acknowledge(stream, group, message.getId());
        } else {
            log.warn("Fallo temporal en evento Collab {} (intento {}/{}): {}",
                    message.getId(), deliveryCount, maxRetries, error.getMessage());
        }
    }

    private long getDeliveryCount(RecordId recordId) {
        try {
            PendingMessages pending = redis.opsForStream().pending(
                    stream,
                    Consumer.from(group, consumer),
                    Range.just(recordId.getValue()),
                    1
            );
            if (pending != null && !pending.isEmpty()) {
                return pending.get(0).getTotalDeliveryCount();
            }
        } catch (Exception e) {
            log.debug("No se pudo consultar deliveryCount en Redis: {}", e.getMessage());
        }
        return 1;
    }

    private void sendToDlq(MapRecord<String, Object, Object> message, long deliveryCount, Exception error) {
        try {
            String errorMsg = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            Map<String, Object> dlqEntry = Map.of(
                "sourceStream", stream,
                "sourceGroup", group,
                "sourceMessageId", message.getId().getValue(),
                "consumerId", consumer,
                "failedAt", Instant.now().toString(),
                "deliveryCount", String.valueOf(deliveryCount),
                "errorMessage", errorMsg,
                "payload", String.valueOf(message.getValue().getOrDefault("payload", ""))
            );
            redis.opsForStream().add(dlqStream, dlqEntry);
        } catch (Exception dlqError) {
            log.error("Fallo al publicar en DLQ {}: {}", dlqStream, dlqError.getMessage());
        }
    }

    private void ensureConsumerGroup() {
        if (consumerGroupReady) return;
        try {
            redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), group);
            consumerGroupReady = true;
        } catch (DataAccessException firstError) {
            if (hasRedisCode(firstError, "BUSYGROUP")) {
                consumerGroupReady = true;
                return;
            }
            if (!hasRedisCode(firstError, "requires the key to exist")) throw firstError;

            redis.opsForStream().add(stream, Map.of("__bootstrap__", "1"));
            try {
                redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), group);
                consumerGroupReady = true;
            } catch (DataAccessException retryError) {
                if (!hasRedisCode(retryError, "BUSYGROUP")) throw retryError;
                consumerGroupReady = true;
            }
        }
    }

    private static boolean hasRedisCode(Throwable error, String code) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains(code)) return true;
        }
        return false;
    }
}
