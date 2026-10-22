package com.cimaxis.demo.marketing.dto.whatsapp;

import java.time.LocalDateTime;

public record WhatsappSentResponse(Integer interactionId, String deliveryStatus, LocalDateTime sentAt) {
}
