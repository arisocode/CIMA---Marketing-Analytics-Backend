package com.cimaxis.demo.integration.collab.service;

import java.util.List;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumidor manual: sólo confirma Redis después de persistir la proyección.
 * Si falla, el mensaje queda pendiente para reintento, sin perder eventos.
 */
@Component
@ConditionalOnProperty(name = "cimaxis.collab-projection.enabled", havingValue = "true")
public class CollabProjectStreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(CollabProjectStreamConsumer.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ProjectProjectionService projectionService;
    private final String stream;
    private final String group;
    private final String consumer;
    private final int batchSize;

    public CollabProjectStreamConsumer(StringRedisTemplate redis, ObjectMapper objectMapper,
            ProjectProjectionService projectionService,
            @Value("${cimaxis.collab-projection.stream}") String stream,
            @Value("${cimaxis.collab-projection.consumer-group}") String group,
            @Value("${cimaxis.collab-projection.consumer-name}") String consumer,
            @Value("${cimaxis.collab-projection.batch-size}") int batchSize) {
        this.redis = redis;
        this.objectMapper = objectMapper;
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
                JsonNode event = objectMapper.readTree(String.valueOf(message.getValue().get("payload")));
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
        } catch (DataAccessException error) {
            if (error.getMessage() == null || !error.getMessage().contains("BUSYGROUP")) {
                throw error;
            }
        }
    }
}
