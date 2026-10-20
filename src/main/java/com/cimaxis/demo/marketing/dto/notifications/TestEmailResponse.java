package com.cimaxis.demo.marketing.dto.notifications;

/** Resultado del correo de prueba: QUEUED, FAILED, SIMULATED o SKIPPED_LIMIT. */
public record TestEmailResponse(String deliveryStatus, String detail) {
}
