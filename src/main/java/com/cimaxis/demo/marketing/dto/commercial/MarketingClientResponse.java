package com.cimaxis.demo.marketing.dto.commercial;

import java.time.LocalDateTime;

import com.cimaxis.demo.analytics.domain.Client;

/**
 * Cliente del listado de marketing: los mismos campos que antes devolvia la
 * entidad Client, mas el puntaje de interes calculado al vuelo.
 */
public record MarketingClientResponse(String clientId, String userId, String contactInfo, String address,
                                      String additionalInfo, String plan, LocalDateTime createdAt,
                                      LocalDateTime updatedAt, int score) {

    public static MarketingClientResponse of(Client c, int score) {
        return new MarketingClientResponse(c.getClientId(), c.getUserId(), c.getContactInfo(), c.getAddress(),
                c.getAdditionalInfo(), c.getPlan(), c.getCreatedAt(), c.getUpdatedAt(), score);
    }
}
