package com.cimaxis.demo.marketing.service.commercial;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction.InteractionType;

/** Datos de prueba compartidos por las pruebas del flujo comercial. Hoy = 2026-10-21 en Bogota. */
final class CommercialTestData {

    static final LocalDate HOY = LocalDate.of(2026, 10, 21);
    /** 15:00 UTC = 10:00 en Bogota del 21 de octubre. */
    static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC);

    private static int secuencia = 1;

    private CommercialTestData() {
    }

    static Client cliente(String id, String nombre, String correo, String plan, LocalDateTime alta) {
        return Client.builder().clientId(id).userId("u-" + id).additionalInfo(nombre)
                .contactInfo(correo).plan(plan).createdAt(alta).updatedAt(alta).build();
    }

    static MarketingInteraction mensaje(String clientId, LocalDateTime fecha, String deliveryStatus) {
        return MarketingInteraction.builder().interactionId(secuencia++).campaignId(1).clientId(clientId)
                .contactDate(fecha).interactionType(InteractionType.message).channel("email")
                .deliveryStatus(deliveryStatus).build();
    }

    static MarketingInteraction respuesta(String clientId, LocalDateTime fecha, String texto) {
        return MarketingInteraction.builder().interactionId(secuencia++).campaignId(1).clientId(clientId)
                .contactDate(fecha).interactionType(InteractionType.message).channel("email")
                .response(texto).build();
    }

    static MarketingInteraction deTipo(String clientId, LocalDateTime fecha, InteractionType tipo) {
        return MarketingInteraction.builder().interactionId(secuencia++).campaignId(1).clientId(clientId)
                .contactDate(fecha).interactionType(tipo).build();
    }

    static Proposal propuesta(int id, String clientId, Proposal.ProposalStatus estado, LocalDate creada,
                              LocalDate respondida, String valor, String projectId) {
        return Proposal.builder().proposalId(id).clientId(clientId).status(estado)
                .description("Propuesta " + id).createdDate(creada).responseDate(respondida)
                .estimatedValue(valor != null ? new BigDecimal(valor) : null).projectId(projectId)
                .createdAt(creada != null ? creada.atStartOfDay() : null)
                .updatedAt(respondida != null ? respondida.atTime(12, 0) : null).build();
    }

    static Project proyecto(String id, String clientId, LocalDateTime creado) {
        return Project.builder().projectId(id).clientId(clientId).projectName("Proyecto " + id)
                .status("in_progress").createdAt(creado).updatedAt(creado).build();
    }

    static LocalDateTime dia(int mes, int dia) {
        return LocalDateTime.of(2026, mes, dia, 9, 0);
    }
}
