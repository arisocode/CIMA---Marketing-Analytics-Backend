package com.cimaxis.demo.marketing.service.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.marketing.domain.templates.MessageTemplate;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowRequest;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowResponse;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.templates.MessageTemplateService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private MessageTemplateService templateService;

    private WorkflowService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowService(workflowRepository, campaignRepository, new WorkflowMapper(), templateService);
        when(campaignRepository.existsById(20)).thenReturn(true);
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsAWorkflowLinkedToAnActiveTemplate() {
        UUID plantilla = UUID.randomUUID();
        when(templateService.requireActive(plantilla)).thenReturn(MessageTemplate.builder()
                .templateId(plantilla).name("Bienvenida").active(true).createdAt(LocalDateTime.now()).build());

        WorkflowResponse creado = service.create(request(plantilla));

        assertThat(creado.getTemplateId()).isEqualTo(plantilla);
        verify(templateService).requireActive(plantilla);
    }

    @Test
    void rejectsAnInactiveTemplateAndSavesNothing() {
        UUID plantilla = UUID.randomUUID();
        when(templateService.requireActive(plantilla))
                .thenThrow(new IllegalArgumentException("La plantilla Bienvenida está inactiva"));

        assertThatThrownBy(() -> service.create(request(plantilla)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactiva");
        verify(workflowRepository, never()).save(any());
    }

    @Test
    void aWorkflowWithoutTemplateDoesNotQueryTheLibrary() {
        assertThat(service.create(request(null)).getTemplateId()).isNull();
        verify(templateService, never()).requireActive(any());
    }

    private static WorkflowRequest request(UUID templateId) {
        return WorkflowRequest.builder().campaignId(20).workflowName("Bienvenida")
                .triggerType("manual").actionType("send_email").messageTemplate("Hola {nombre}")
                .templateId(templateId).build();
    }
}
