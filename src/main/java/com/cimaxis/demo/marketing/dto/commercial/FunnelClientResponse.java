package com.cimaxis.demo.marketing.dto.commercial;

import java.time.LocalDateTime;

public record FunnelClientResponse(String clientId, String name, String plan, LocalDateTime lastContact, int score) {
}
