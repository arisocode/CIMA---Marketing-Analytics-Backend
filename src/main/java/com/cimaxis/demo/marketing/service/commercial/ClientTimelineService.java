package com.cimaxis.demo.marketing.service.commercial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.dto.commercial.ClientTimelineResponse;
import com.cimaxis.demo.marketing.dto.commercial.TimelineClient;
import com.cimaxis.demo.marketing.dto.commercial.TimelineEvent;
import com.cimaxis.demo.marketing.dto.commercial.TimelineStats;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.service.campaigns.CampaignAudienceService;

/**
 * Ficha 360° de un cliente: datos, estadisticas y linea de tiempo (mas reciente
 * primero). stats.interactions y stats.responses usan las mismas definiciones de
 * contacto y respuesta que los KPI; los mensajes omitidos por el limite diario
 * aparecen en la linea de tiempo con su estado, pero no cuentan como interaccion.
 */
@Service
public class ClientTimelineService {

    public static final String PLAN = "PLAN";
    public static final String CAMPAIGN = "CAMPAIGN";
    public static final String MESSAGE = "MESSAGE";
    public static final String RESPONSE = "RESPONSE";
    public static final String PROPOSAL = "PROPOSAL";
    public static final String PROPOSAL_DECISION = "PROPOSAL_DECISION";
    public static final String PROJECT = "PROJECT";

    private final ClientRepository clientRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final ProposalRepository proposalRepository;
    private final ProjectRepository projectRepository;
    private final CampaignRepository campaignRepository;
    private final ClientScoreService scoreService;
    private final CampaignAudienceService audienceService;

    public ClientTimelineService(ClientRepository clientRepository,
                                 MarketingInteractionRepository interactionRepository,
                                 ProposalRepository proposalRepository,
                                 ProjectRepository projectRepository,
                                 CampaignRepository campaignRepository,
                                 ClientScoreService scoreService,
                                 CampaignAudienceService audienceService) {
        this.clientRepository = clientRepository;
        this.interactionRepository = interactionRepository;
        this.proposalRepository = proposalRepository;
        this.projectRepository = projectRepository;
        this.campaignRepository = campaignRepository;
        this.scoreService = scoreService;
        this.audienceService = audienceService;
    }

    public ClientTimelineResponse timeline(String clientId) {
        String id = clientId == null ? null : clientId.trim();
        Client client = (id == null || id.isEmpty() ? java.util.Optional.<Client>empty() : clientRepository.findById(id))
                .orElseThrow(() -> ResourceNotFoundException.de("Cliente", clientId));

        List<MarketingInteraction> interacciones = nullSafe(interactionRepository.findByClientId(id));
        List<Proposal> propuestas = nullSafe(proposalRepository.findByClientId(id));
        List<Project> proyectos = nullSafe(projectRepository.findByClientId(id));
        List<Campaign> campanas = campanas(id, interacciones);

        CommercialFacts.ClientFacts hechos = CommercialFacts.from(interacciones, propuestas, proyectos).of(id);
        LocalDate hoy = scoreService.hoy();

        TimelineClient datos = new TimelineClient(id, CommercialFacts.nombre(client), client.getContactInfo(),
                client.getPlan(), client.getCreatedAt(), hechos.lastContact(),
                InterestScore.calcular(client, hechos, hoy));

        Set<String> idsProyecto = new LinkedHashSet<>();
        proyectos.forEach(p -> { if (p.getProjectId() != null) idsProyecto.add(p.getProjectId()); });
        propuestas.stream().filter(CommercialFacts::tieneProyectoVinculado)
                .forEach(p -> idsProyecto.add(p.getProjectId().trim()));

        TimelineStats stats = new TimelineStats(
                interacciones.stream().filter(KpiCalculationService::esContacto).count(),
                interacciones.stream().filter(i -> KpiCalculationService.esContacto(i)
                        && KpiCalculationService.esRespuesta(i)).count(),
                propuestas.size(),
                propuestas.stream()
                        .filter(p -> p.getStatus() == ProposalStatus.Approved && p.getEstimatedValue() != null)
                        .map(Proposal::getEstimatedValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP),
                idsProyecto.size());

        List<TimelineEvent> eventos = new ArrayList<>();
        if (client.getPlan() != null && !client.getPlan().isBlank()) {
            eventos.add(new TimelineEvent(PLAN, primera(client.getUpdatedAt(), client.getCreatedAt()),
                    "Plan " + client.getPlan(), "Plan comercial asignado al cliente", client.getPlan()));
        }
        for (Campaign c : campanas) {
            eventos.add(new TimelineEvent(CAMPAIGN,
                    primera(c.getStartDate() != null ? c.getStartDate().atStartOfDay() : null, c.getCreatedAt()),
                    "Campaña " + texto(c.getCampaignName(), "#" + c.getCampaignId()),
                    unir(c.getCampaignType() != null ? "Tipo: " + c.getCampaignType().name() : null,
                         c.audienceOrDefault() != Campaign.AudienceType.CLIENT
                                 ? "Audiencia: " + audienceService.describe(c, null) : null,
                         c.getStatus() != null ? "Estado: " + c.getStatus().name() : null),
                    String.valueOf(c.getCampaignId())));
        }
        for (MarketingInteraction i : interacciones) {
            eventos.add(eventoDeInteraccion(i));
        }
        Set<String> proyectosEnTabla = new LinkedHashSet<>();
        for (Project p : proyectos) {
            proyectosEnTabla.add(p.getProjectId());
            eventos.add(new TimelineEvent(PROJECT, primera(p.getCreatedAt(), p.getUpdatedAt()),
                    "Proyecto " + texto(p.getProjectName(), p.getProjectId()),
                    p.getStatus() != null ? "Estado: " + p.getStatus() : null, p.getProjectId()));
        }
        for (Proposal p : propuestas) {
            String ref = String.valueOf(p.getProposalId());
            eventos.add(new TimelineEvent(PROPOSAL,
                    primera(p.getCreatedDate() != null ? p.getCreatedDate().atStartOfDay() : null, p.getCreatedAt()),
                    "Propuesta: " + CommercialFacts.tituloPropuesta(p),
                    unir(valor(p.getEstimatedValue()), p.getStatus() != null ? "Estado: " + p.getStatus().name() : null),
                    ref));
            if ((p.getStatus() == ProposalStatus.Approved || p.getStatus() == ProposalStatus.Rejected)
                    && p.getResponseDate() != null) {
                boolean aprobada = p.getStatus() == ProposalStatus.Approved;
                eventos.add(new TimelineEvent(PROPOSAL_DECISION, p.getResponseDate().atStartOfDay(),
                        (aprobada ? "Propuesta aprobada: " : "Propuesta rechazada: ") + CommercialFacts.tituloPropuesta(p),
                        valor(p.getEstimatedValue()), ref));
            }
            if (CommercialFacts.tieneProyectoVinculado(p) && !proyectosEnTabla.contains(p.getProjectId().trim())) {
                eventos.add(new TimelineEvent(PROJECT, primera(p.getUpdatedAt(), p.getCreatedAt()),
                        "Proyecto vinculado a la propuesta #" + p.getProposalId(),
                        "Proyecto de CIMAxios " + p.getProjectId().trim(), p.getProjectId().trim()));
            }
        }

        eventos.sort(Comparator.comparing(TimelineEvent::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return new ClientTimelineResponse(datos, stats, eventos);
    }

    private static TimelineEvent eventoDeInteraccion(MarketingInteraction i) {
        String canal = i.getChannel() != null && !i.getChannel().isBlank() ? i.getChannel().trim() : null;
        String tipo = i.getInteractionType() != null ? "Tipo: " + i.getInteractionType().name() : null;
        String entrega = i.getDeliveryStatus() != null && !i.getDeliveryStatus().isBlank()
                ? "Estado de entrega: " + i.getDeliveryStatus() : null;
        String error = i.getDeliveryError() != null && !i.getDeliveryError().isBlank() ? i.getDeliveryError().trim() : null;
        String ref = String.valueOf(i.getInteractionId());

        if (KpiCalculationService.esRespuesta(i)) {
            String respuesta = i.getResponse() != null && !i.getResponse().isBlank() ? i.getResponse().trim() : null;
            return new TimelineEvent(RESPONSE, i.getContactDate(),
                    canal != null ? "Respuesta del cliente por " + canal : "Respuesta del cliente",
                    unir(respuesta, tipo, entrega), ref);
        }
        return new TimelineEvent(MESSAGE, i.getContactDate(),
                canal != null ? "Mensaje por " + canal : "Mensaje",
                unir(tipo, entrega, error), ref);
    }

    /**
     * Campañas cuya audiencia actual incluye al cliente (suyas, de un segmento que lo
     * contiene o de todos los clientes) y campañas en las que recibio interacciones.
     */
    private List<Campaign> campanas(String clientId, List<MarketingInteraction> interacciones) {
        Map<Integer, Campaign> porId = new LinkedHashMap<>();
        for (Campaign c : nullSafe(audienceService.campaignsForClient(clientId))) {
            porId.put(c.getCampaignId(), c);
        }
        Set<Integer> faltantes = new LinkedHashSet<>();
        for (MarketingInteraction i : interacciones) {
            if (i.getCampaignId() != null && !porId.containsKey(i.getCampaignId())) faltantes.add(i.getCampaignId());
        }
        if (!faltantes.isEmpty()) {
            for (Campaign c : nullSafe(campaignRepository.findAllById(faltantes))) porId.put(c.getCampaignId(), c);
        }
        return new ArrayList<>(porId.values());
    }

    private static String valor(BigDecimal valor) {
        return valor != null ? "Valor: " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString() : null;
    }

    private static String unir(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String texto(String valor, String porDefecto) {
        return valor != null && !valor.isBlank() ? valor.trim() : porDefecto;
    }

    private static LocalDateTime primera(LocalDateTime a, LocalDateTime b) {
        return a != null ? a : b;
    }

    private static <T> List<T> nullSafe(List<T> lista) {
        return lista != null ? lista : List.of();
    }
}
