package com.cimaxis.demo.marketing.dto.templates;

import java.time.LocalDateTime;
import java.util.UUID;

public record TemplateResponse(
        UUID templateId,
        String name,
        String channel,
        String plan,
        String subject,
        String body,
        Boolean active,
        LocalDateTime createdAt) {
}
