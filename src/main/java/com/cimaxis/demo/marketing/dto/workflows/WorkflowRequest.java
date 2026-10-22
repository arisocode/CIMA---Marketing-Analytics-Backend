package com.cimaxis.demo.marketing.dto.workflows;

import java.util.UUID;

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
public class WorkflowRequest {

    private Integer campaignId;
    private String workflowName;
    private String description;
    private String triggerType;
    private Integer noContactDays;
    /** Fecha y hora (America/Bogota) del disparador scheduled_date, p. ej. "2026-12-01T09:00:00". */
    private java.time.LocalDateTime scheduledAt;
    private String actionType;
    private String messageTemplate;
    /** Plantilla opcional de la biblioteca; si viene, reemplaza a messageTemplate al enviar. */
    private UUID templateId;
    private Boolean active;
}
