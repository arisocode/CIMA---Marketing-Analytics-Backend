package com.cimaxis.demo.marketing.service.commercial;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts.ClientFacts;

/**
 * Puntaje de interes comercial de un cliente, de 0 a 100. Se calcula al vuelo
 * cada vez que se consulta; no se guarda en base de datos.
 *
 * <pre>
 *   +30  si respondio en los ultimos 30 dias (ultima respuesta hace 30 dias o menos)
 *   +20  si tiene una propuesta abierta (Sent o In_negotiation)
 *   +30  si tiene una propuesta Approved
 *   +20  si tiene proyecto (propuesta Approved con projectId, o proyecto en PROJECTS)
 *   -1   por cada dia sin contacto por encima de 15 (con 20 dias sin contacto resta 5)
 *   resultado = max(0, min(100, suma))
 * </pre>
 *
 * Dias sin contacto se cuentan desde el ultimo contacto; si el cliente nunca fue
 * contactado, desde su fecha de alta; sin ninguna de las dos fechas no se descuenta.
 * Contacto = interaccion que no es SKIPPED_LIMIT; respuesta =
 * KpiCalculationService.esRespuesta. Los dias se cuentan en America/Bogota.
 */
public final class InterestScore {

    public static final int PUNTOS_RESPUESTA_RECIENTE = 30;
    public static final int DIAS_RESPUESTA_RECIENTE = 30;
    public static final int PUNTOS_PROPUESTA_ABIERTA = 20;
    public static final int PUNTOS_PROPUESTA_APROBADA = 30;
    public static final int PUNTOS_PROYECTO = 20;
    public static final int DIAS_SIN_CONTACTO_TOLERADOS = 15;

    private InterestScore() {
    }

    public static int calcular(Client client, ClientFacts hechos, LocalDate hoy) {
        ClientFacts h = hechos != null ? hechos : ClientFacts.EMPTY;
        int puntaje = 0;

        if (h.lastResponse() != null
                && ChronoUnit.DAYS.between(h.lastResponse().toLocalDate(), hoy) <= DIAS_RESPUESTA_RECIENTE) {
            puntaje += PUNTOS_RESPUESTA_RECIENTE;
        }
        if (h.openProposal()) puntaje += PUNTOS_PROPUESTA_ABIERTA;
        if (h.approvedProposal()) puntaje += PUNTOS_PROPUESTA_APROBADA;
        if (h.project()) puntaje += PUNTOS_PROYECTO;

        LocalDateTime referencia = h.lastContact() != null ? h.lastContact()
                : client != null ? client.getCreatedAt() : null;
        if (referencia != null) {
            long dias = ChronoUnit.DAYS.between(referencia.toLocalDate(), hoy);
            if (dias > DIAS_SIN_CONTACTO_TOLERADOS) {
                puntaje -= (int) Math.min(Integer.MAX_VALUE, dias - DIAS_SIN_CONTACTO_TOLERADOS);
            }
        }
        return Math.max(0, Math.min(100, puntaje));
    }

    /** Dias completos desde el ultimo contacto; null si nunca fue contactado. */
    public static Long diasSinContacto(LocalDateTime ultimoContacto, LocalDate hoy) {
        return ultimoContacto == null ? null : ChronoUnit.DAYS.between(ultimoContacto.toLocalDate(), hoy);
    }
}
