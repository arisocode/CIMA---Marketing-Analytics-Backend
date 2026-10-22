package com.cimaxis.demo.marketing.dto.workflows;

/** Cliente de la audiencia que no recibe la accion, con el motivo en español. */
public record WorkflowSkippedClient(String clientId, String name, String reason) {
}
