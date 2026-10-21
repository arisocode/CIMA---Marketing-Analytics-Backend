package com.cimaxis.demo.marketing.service.commercial;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;

/**
 * Hechos comerciales por cliente a partir de interacciones, propuestas y
 * proyectos. Lo comparten el embudo, el puntaje de interes, la ficha 360° y el
 * panel Hoy para que ninguno contradiga a los otros ni a los KPI:
 * contacto y respuesta usan KpiCalculationService.esContacto y esRespuesta.
 */
public final class CommercialFacts {

    /** Propuestas que ya se enviaron al cliente (etapa "Propuesta enviada" del embudo). */
    public static final Set<ProposalStatus> ESTADOS_ENVIADA = EnumSet.of(
            ProposalStatus.Sent, ProposalStatus.In_negotiation, ProposalStatus.Approved, ProposalStatus.Rejected);

    /** Propuestas abiertas, esperando decision del cliente. */
    public static final Set<ProposalStatus> ESTADOS_ABIERTA = EnumSet.of(
            ProposalStatus.Sent, ProposalStatus.In_negotiation);

    /** Resumen de un cliente. lastContact y lastResponse son null si nunca hubo. */
    public record ClientFacts(LocalDateTime lastContact, LocalDateTime lastResponse,
                              boolean openProposal, boolean approvedProposal, boolean project) {
        public static final ClientFacts EMPTY = new ClientFacts(null, null, false, false, false);
    }

    private final Map<String, ClientFacts> porCliente;

    private CommercialFacts(Map<String, ClientFacts> porCliente) {
        this.porCliente = porCliente;
    }

    public ClientFacts of(String clientId) {
        return clientId == null ? ClientFacts.EMPTY : porCliente.getOrDefault(clientId, ClientFacts.EMPTY);
    }

    public static CommercialFacts from(List<MarketingInteraction> interacciones,
                                       List<Proposal> propuestas,
                                       List<Project> proyectos) {
        Map<String, Acumulado> datos = new HashMap<>();

        for (MarketingInteraction interaction : nullSafe(interacciones)) {
            if (!KpiCalculationService.esContacto(interaction)) continue;
            Acumulado a = datos.computeIfAbsent(interaction.getClientId(), k -> new Acumulado());
            a.lastContact = masReciente(a.lastContact, interaction.getContactDate());
            if (KpiCalculationService.esRespuesta(interaction)) {
                a.respondio = true;
                a.lastResponse = masReciente(a.lastResponse, interaction.getContactDate());
            }
        }
        for (Proposal proposal : nullSafe(propuestas)) {
            if (proposal.getClientId() == null || proposal.getStatus() == null) continue;
            Acumulado a = datos.computeIfAbsent(proposal.getClientId(), k -> new Acumulado());
            if (ESTADOS_ABIERTA.contains(proposal.getStatus())) a.openProposal = true;
            if (proposal.getStatus() == ProposalStatus.Approved) a.approvedProposal = true;
            if (tieneProyectoVinculado(proposal)) a.project = true;
        }
        for (Project project : nullSafe(proyectos)) {
            if (project.getClientId() == null) continue;
            datos.computeIfAbsent(project.getClientId(), k -> new Acumulado()).project = true;
        }

        Map<String, ClientFacts> resultado = new HashMap<>();
        datos.forEach((id, a) -> resultado.put(id, new ClientFacts(
                a.lastContact, a.respondio ? a.lastResponse : null,
                a.openProposal, a.approvedProposal, a.project)));
        return new CommercialFacts(resultado);
    }

    /** Propuesta aprobada con proyecto de CIMAxios vinculado. */
    public static boolean tieneProyectoVinculado(Proposal proposal) {
        return proposal != null
                && proposal.getStatus() == ProposalStatus.Approved
                && proposal.getProjectId() != null
                && !proposal.getProjectId().isBlank();
    }

    /** Nombre visible: additionalInfo del cliente o, si no hay, contactInfo. */
    public static String nombre(Client client) {
        if (client == null) return null;
        if (client.getAdditionalInfo() != null && !client.getAdditionalInfo().isBlank()) {
            return client.getAdditionalInfo().trim();
        }
        if (client.getContactInfo() != null && !client.getContactInfo().isBlank()) {
            return client.getContactInfo().trim();
        }
        return null;
    }

    /** Titulo visible de una propuesta (no tiene campo titulo): primera linea de la descripcion. */
    public static String tituloPropuesta(Proposal proposal) {
        String descripcion = proposal.getDescription();
        if (descripcion != null && !descripcion.isBlank()) {
            String linea = descripcion.strip().split("\\R", 2)[0].strip();
            return linea.length() > 80 ? linea.substring(0, 77) + "..." : linea;
        }
        return "Propuesta #" + proposal.getProposalId();
    }

    private static LocalDateTime masReciente(LocalDateTime actual, LocalDateTime nueva) {
        if (nueva == null) return actual;
        return actual == null || nueva.isAfter(actual) ? nueva : actual;
    }

    private static <T> List<T> nullSafe(List<T> lista) {
        return lista != null ? lista : List.of();
    }

    private static final class Acumulado {
        LocalDateTime lastContact;
        LocalDateTime lastResponse;
        boolean respondio;
        boolean openProposal;
        boolean approvedProposal;
        boolean project;
    }
}
