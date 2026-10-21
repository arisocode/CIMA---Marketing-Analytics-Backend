package com.cimaxis.demo.marketing.domain.messages;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mensaje de la conversacion comercial entre CIMA y un cliente, previa al
 * proyecto (distinta del chat por proyecto de CIMAxios).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "marketing_messages",
       indexes = @Index(name = "idx_marketing_messages_client_created", columnList = "client_id, created_at"))
public class MarketingMessage {

    public static final int LONGITUD_MAXIMA_CUERPO = 4000;

    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "client_id", nullable = false, length = 36)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "author_sub", length = 36)
    private String authorSub;

    @Column(name = "author_name", length = 120)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** OUTBOUND = CIMA hacia el cliente; INBOUND = cliente hacia CIMA. */
    public enum Direction {
        OUTBOUND, INBOUND
    }
}
