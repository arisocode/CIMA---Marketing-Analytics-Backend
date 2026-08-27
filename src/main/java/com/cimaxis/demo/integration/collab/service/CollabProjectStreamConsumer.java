package com.cimaxis.demo.integration.collab.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor manual: sólo confirma Redis después de persistir la proyección.
 * Si falla, el mensaje queda pendiente para reintento, sin perder eventos.
 */
@Component
@ConditionalOnProperty(name = "cimaxis.collab-projection.enabled", havingValue = "true")
public class CollabProjectStreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(CollabProjectStreamConsumer.class);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final ProjectProjectionService projectionService;
    private final String stream;
    private final String group;
    private final String consumer;
    private final int batchSize;

    public CollabProjectStreamConsumer(StringRedisTemplate redis, JsonMapper jsonMapper,
            ProjectProjectionService projectionService,
            @Value("${cimaxis.collab-projection.stream}") String stream,
            @Value("${cimaxis.collab-projection.consumer-group}") String group,
            @Value("${cimaxis.collab-projection.consumer-name}") String consumer,
            @Value("${cimaxis.collab-projection.batch-size}") int batchSize) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.projectionService = projectionService;
        this.stream = stream;
        this.group = group;
        this.consumer = consumer;
        this.batchSize = batchSize;
        ensureConsumerGroup();
    }

    @Scheduled(fixedDelayString = "${cimaxis.collab-projection.poll-delay-ms}")
    public void consume() {
        List<MapRecord<String, Object, Object>> messages = redis.opsForStream().read(
                Consumer.from(group, consumer), StreamReadOptions.empty().count(batchSize),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
        if (messages == null) return;
        for (MapRecord<String, Object, Object> message : messages) {
            try {
                JsonNode event = jsonMapper.readTree(String.valueOf(message.getValue().get("payload")));
                String type = event.path("type").asText();
                if ("project.created".equals(type) || "project.updated".equals(type)) {
                    projectionService.apply(event);
                }
                redis.opsForStream().acknowledge(stream, group, message.getId());
            } catch (Exception error) {
                log.error("No se pudo proyectar evento Collab {}: {}", message.getId(), error.getMessage(), error);
            }
        }
    }

    private void ensureConsumerGroup() {
        try {
            redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), group);
        } catch (DataAccessException firstError) {
            if (hasRedisCode(firstError, "BUSYGROUP")) return;
            if (!hasRedisCode(firstError, "requires the key to exist")) throw firstError;

            redis.opsForStream().add(stream, Map.of("__bootstrap__", "1"));
            try {
                redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), group);
            } catch (DataAccessException retryError) {
                if (!hasRedisCode(retryError, "BUSYGROUP")) throw retryError;
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
