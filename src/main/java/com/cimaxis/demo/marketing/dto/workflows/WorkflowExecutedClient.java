package com.cimaxis.demo.marketing.dto.workflows;

/** Resultado de ejecutar la accion para un cliente: result success|failed|pending. */
public record WorkflowExecutedClient(String clientId, String name, String result, String deliveryStatus,
                                     String detail) {
}
