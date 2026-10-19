package com.cimaxis.demo.marketing.dto.templates;

/** Asunto y cuerpo con las variables reemplazadas. subject es null si la plantilla no tiene asunto. */
public record TemplatePreviewResponse(String subject, String body) {
}
