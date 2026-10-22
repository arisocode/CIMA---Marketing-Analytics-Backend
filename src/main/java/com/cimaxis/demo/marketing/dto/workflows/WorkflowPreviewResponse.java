package com.cimaxis.demo.marketing.dto.workflows;

import java.time.LocalDateTime;
import java.util.List;

/** Vista previa: a quien le llegaria hoy la automatizacion y por que no a los demas. */
public record WorkflowPreviewResponse(String campaignName, boolean campaignActive, String audienceType,
                                      int audienceSize, LocalDateTime nextRunAt,
                                      List<WorkflowAudienceClient> eligible,
                                      List<WorkflowSkippedClient> skipped) {
}
