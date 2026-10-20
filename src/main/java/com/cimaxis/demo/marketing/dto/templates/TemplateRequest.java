package com.cimaxis.demo.marketing.dto.templates;

/** Datos para crear o editar una plantilla. channel: EMAIL, WHATSAPP o IN_APP. */
public record TemplateRequest(String name, String channel, String plan, String subject, String body) {
}
