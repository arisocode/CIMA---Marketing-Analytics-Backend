package com.cimaxis.demo.integration.collab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.integration.collab.domain.ProcessedCollabEvent;
import com.cimaxis.demo.integration.collab.repository.ProcessedCollabEventRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProjectProjectionServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProcessedCollabEventRepository processedEventRepository;

    private ProjectProjectionService service;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new ProjectProjectionService(projectRepository, processedEventRepository);
    }

    @Test
    void appliesProjectEventWithOptionalClientSub() throws Exception {
        String json = """
            {
              "id": "evt-100",
              "type": "project.created",
              "timestamp": "2026-09-28T12:00:00Z",
              "data": {
                "projectId": "proj-1",
                "projectName": "Proyecto Sin Cliente",
                "status": "active"
              }
            }
            """;
        JsonNode event = jsonMapper.readTree(json);

        when(processedEventRepository.existsById("evt-100")).thenReturn(false);
        when(projectRepository.findById("proj-1")).thenReturn(Optional.empty());

        service.apply(event);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();

        assertThat(saved.getProjectId()).isEqualTo("proj-1");
        assertThat(saved.getClientId()).isNull();
        assertThat(saved.getProjectName()).isEqualTo("Proyecto Sin Cliente");
        assertThat(saved.getStatus()).isEqualTo("active");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        verify(processedEventRepository).save(any(ProcessedCollabEvent.class));
    }

    @Test
    void appliesProjectEventWithExplicitClientSubAndTimestamps() throws Exception {
        String json = """
            {
              "id": "evt-200",
              "type": "project.created",
              "data": {
                "projectId": "proj-2",
                "clientSub": "client-999",
                "projectName": "Proyecto Con Cliente",
                "status": "in_progress",
                "createdAt": "2026-09-01T10:00:00Z",
                "updatedAt": "2026-09-02T15:30:00Z"
              }
            }
            """;
        JsonNode event = jsonMapper.readTree(json);

        when(processedEventRepository.existsById("evt-200")).thenReturn(false);
        when(projectRepository.findById("proj-2")).thenReturn(Optional.empty());

        service.apply(event);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();

        assertThat(saved.getProjectId()).isEqualTo("proj-2");
        assertThat(saved.getClientId()).isEqualTo("client-999");
        assertThat(saved.getProjectName()).isEqualTo("Proyecto Con Cliente");
        assertThat(saved.getStatus()).isEqualTo("in_progress");
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0, 0));
        assertThat(saved.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 15, 30, 0));
    }

    @Test
    void ignoresAlreadyProcessedEventIdempotently() throws Exception {
        String json = """
            {
              "id": "evt-already-processed",
              "type": "project.created",
              "data": { "projectId": "p-1" }
            }
            """;
        JsonNode event = jsonMapper.readTree(json);

        when(processedEventRepository.existsById("evt-already-processed")).thenReturn(true);

        service.apply(event);

        verify(projectRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }
}
