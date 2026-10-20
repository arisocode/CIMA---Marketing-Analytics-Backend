package com.cimaxis.demo.marketing.dto.workflows;

import java.util.UUID;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowResponse {

    private Integer workflowId;
    private Integer campaignId;
    private String workflowName;
    private String description;
    private String triggerType;
    private Integer noContactDays;
    private String actionType;
    private String messageTemplate;
    /** Plantilla opcional de la biblioteca; si viene, reemplaza a messageTemplate al enviar. */
    private UUID templateId;
    private Boolean active;
    private LocalDateTime createdAt;
}
