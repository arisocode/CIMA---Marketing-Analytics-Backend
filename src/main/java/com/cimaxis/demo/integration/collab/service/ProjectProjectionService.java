package com.cimaxis.demo.integration.collab.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.integration.collab.domain.ProcessedCollabEvent;
import com.cimaxis.demo.integration.collab.repository.ProcessedCollabEventRepository;
import tools.jackson.databind.JsonNode;

/** Aplica una instantánea de proyecto en la base de Marketing de forma idempotente. */
@Service
public class ProjectProjectionService {

    private final ProjectRepository projects;
    private final ProcessedCollabEventRepository processedEvents;

    public ProjectProjectionService(ProjectRepository projects,
                                    ProcessedCollabEventRepository processedEvents) {
        this.projects = projects;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void apply(JsonNode event) {
        String eventId = requiredText(event, "id");
        if (processedEvents.existsById(eventId)) {
            return;
        }

        JsonNode data = event.path("data");
        String projectId = requiredText(data, "projectId");
        String clientId = data.path("clientSub").asText(null);
        if (clientId != null && clientId.isBlank()) {
            clientId = null;
        }

        LocalDateTime updatedAt = resolveTimestamp(data.path("updatedAt").asText(null), event);
        LocalDateTime createdAt = resolveTimestamp(data.path("createdAt").asText(null), event);

        Project project = projects.findById(projectId).orElseGet(Project::new);
        if (project.getUpdatedAt() != null && project.getUpdatedAt().isAfter(updatedAt)) {
            processedEvents.save(new ProcessedCollabEvent(eventId));
            return;
        }

        project.setProjectId(projectId);
        project.setClientId(clientId);
        project.setProjectName(data.path("projectName").asText("Proyecto sin nombre"));
        project.setDescription(data.path("description").isNull() ? null : data.path("description").asText(null));
        project.setStatus(data.path("status").asText("active"));
        project.setCreatedAt(createdAt);
        project.setUpdatedAt(updatedAt);
        projects.save(project);
        processedEvents.save(new ProcessedCollabEvent(eventId));
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Evento de proyecto sin campo requerido: " + field);
        }
        return value;
    }

    private static LocalDateTime resolveTimestamp(String explicitTimestamp, JsonNode event) {
        if (explicitTimestamp != null && !explicitTimestamp.isBlank()) {
            try {
                return OffsetDateTime.parse(explicitTimestamp).toLocalDateTime();
            } catch (Exception ignored) {
            }
        }
        String eventTime = event.path("timestamp").asText(null);
        if (eventTime != null && !eventTime.isBlank()) {
            try {
                return OffsetDateTime.parse(eventTime).toLocalDateTime();
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }
}
