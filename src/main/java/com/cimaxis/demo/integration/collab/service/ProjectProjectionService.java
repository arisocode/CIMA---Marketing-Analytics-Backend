package com.cimaxis.demo.integration.collab.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.integration.collab.domain.ProcessedCollabEvent;
import com.cimaxis.demo.integration.collab.repository.ProcessedCollabEventRepository;
import com.fasterxml.jackson.databind.JsonNode;

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
        String clientId = requiredText(data, "clientSub");
        LocalDateTime updatedAt = parseTimestamp(requiredText(data, "updatedAt"));

        Project project = projects.findById(projectId).orElseGet(Project::new);
        if (project.getUpdatedAt() != null && project.getUpdatedAt().isAfter(updatedAt)) {
            processedEvents.save(new ProcessedCollabEvent(eventId));
            return;
        }

        project.setProjectId(projectId);
        project.setClientId(clientId);
        project.setProjectName(requiredText(data, "projectName"));
        project.setDescription(data.path("description").isNull() ? null : data.path("description").asText(null));
        project.setStatus(requiredText(data, "status"));
        project.setCreatedAt(parseTimestamp(requiredText(data, "createdAt")));
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

    private static LocalDateTime parseTimestamp(String value) {
        return OffsetDateTime.parse(value).toLocalDateTime();
    }
}
