package com.cimaxis.demo.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.dto.KpiComparisonDto;
import com.cimaxis.demo.analytics.dto.KpiDetailDto;
import com.cimaxis.demo.analytics.dto.KpiDetailItemDto;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.dto.ProjectAnalyticsDto;
import com.cimaxis.demo.analytics.mapper.KpiSnapshotMapper;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.KpiSnapshotRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.service.campaigns.CampaignAudienceService;

@Service
public class KpiDetailService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final KpiCalculationService kpiCalculationService;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final ProposalRepository proposalRepository;
    private final KpiSnapshotRepository kpiSnapshotRepository;
    private final KpiSnapshotMapper kpiSnapshotMapper;
    private final CampaignAudienceService audienceService;

    public KpiDetailService(KpiCalculationService kpiCalculationService,
                            ClientRepository clientRepository,
                            ProjectRepository projectRepository,
                            ProposalRepository proposalRepository,
                            KpiSnapshotRepository kpiSnapshotRepository,
                            KpiSnapshotMapper kpiSnapshotMapper,
                            CampaignAudienceService audienceService) {
        this.kpiCalculationService = kpiCalculationService;
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.proposalRepository = proposalRepository;
        this.kpiSnapshotRepository = kpiSnapshotRepository;
        this.kpiSnapshotMapper = kpiSnapshotMapper;
        this.audienceService = audienceService;
    }

    public KpiDetailDto getDetail(String kpiKey, String period) {
        KpiType kpi = KpiType.fromKey(kpiKey);
        KpiSelection seleccion = kpiCalculationService.select(period);
        Map<String, Client> clientes = cargarClientes(clientIdsDe(kpi, seleccion));

        List<KpiDetailItemDto> items = switch (kpi) {
            case NEW_CLIENTS -> seleccion.newClients().stream()
                    .map(c -> new KpiDetailItemDto(c.getClientId(), nombreCliente(c, c.getClientId()),
                            c.getPlan() != null ? c.getPlan() : "Sin plan",
                            c.getCreatedAt(), null, null, c.getClientId(), nombreCliente(c, c.getClientId())))
                    .toList();

            case CLOSED_PROJECTS -> seleccion.closedProjects().stream()
                    .map(p -> itemProyecto(p, clientes, null))
                    .toList();

            case REVENUE -> seleccion.approvedProposals().stream()
                    .map(p -> new KpiDetailItemDto(String.valueOf(p.getProposalId()),
                            tituloPropuesta(p),
                            nombreCliente(clientes.get(p.getClientId()), p.getClientId()),
                            p.getResponseDate() != null ? p.getResponseDate().atStartOfDay() : null,
                            p.getEstimatedValue(),
                            p.getStatus() != null ? p.getStatus().name() : null,
                            p.getClientId(),
                            nombreCliente(clientes.get(p.getClientId()), p.getClientId())))
                    .toList();

            case ACTIVE_CAMPAIGNS -> seleccion.activeCampaigns().stream()
                    .map(c -> new KpiDetailItemDto(String.valueOf(c.getCampaignId()),
                            c.getCampaignName(),
                            c.getCampaignType() != null ? c.getCampaignType().name() : null,
                            c.getStartDate() != null ? c.getStartDate().atStartOfDay() : null,
                            null,
                            c.getStatus() != null ? c.getStatus().name() : null,
                            c.getClientId(),
                            audienciaCampana(c, clientes)))
                    .toList();

            case CLIENTS_CONTACTED -> itemsContacto(seleccion.contactedClients(), clientes, "interacción", "interacciones");

            case RESPONSE_RATE -> itemsContacto(seleccion.respondedClients(), clientes, "respuesta", "respuestas");

            case AVG_CLOSE_DAYS -> seleccion.closeTimes().stream()
                    .map(t -> itemProyecto(t.project(), clientes, BigDecimal.valueOf(t.days())))
                    .toList();

            case PROJECTS_IN_PROGRESS -> seleccion.projectsInProgress().stream()
                    .map(p -> itemProyecto(p, clientes, null))
                    .toList();
        };

        return new KpiDetailDto(kpi.key(), seleccion.period().format(PERIOD_FORMAT),
                items.size(), valorKpi(kpi, seleccion), items);
    }

    static BigDecimal valorKpi(KpiType kpi, KpiSelection seleccion) {
        return switch (kpi) {
            case NEW_CLIENTS -> BigDecimal.valueOf(seleccion.newClients().size());
            case CLOSED_PROJECTS -> BigDecimal.valueOf(seleccion.closedProjects().size());
            case REVENUE -> seleccion.estimatedRevenue();
            case ACTIVE_CAMPAIGNS -> BigDecimal.valueOf(seleccion.activeCampaigns().size());
            case CLIENTS_CONTACTED -> BigDecimal.valueOf(seleccion.contactedClients().size());
            case RESPONSE_RATE -> seleccion.responseRate();
            case AVG_CLOSE_DAYS -> seleccion.avgCloseDays();
            case PROJECTS_IN_PROGRESS -> BigDecimal.valueOf(seleccion.projectsInProgress().size());
        };
    }

    public KpiComparisonDto compare(String period) {
        YearMonth actual = kpiCalculationService.resolvePeriod(period);
        YearMonth anterior = actual.minusMonths(1);
        String periodoActual = actual.format(PERIOD_FORMAT);
        String periodoAnterior = anterior.format(PERIOD_FORMAT);

        KpiSnapshotDto actuales = kpiCalculationService.calculate(periodoActual);
        KpiSnapshotDto previos = kpiSnapshotRepository.findByPeriod(periodoAnterior)
                .map(kpiSnapshotMapper::toDto)
                .orElseGet(() -> kpiCalculationService.calculate(periodoAnterior));

        List<KpiComparisonDto.KpiDelta> deltas = new ArrayList<>();
        for (KpiType kpi : KpiType.values()) {
            BigDecimal actualValor = valorSnapshot(kpi, actuales);
            BigDecimal previoValor = valorSnapshot(kpi, previos);
            deltas.add(new KpiComparisonDto.KpiDelta(kpi.key(), actualValor, previoValor,
                    deltaPercent(actualValor, previoValor)));
        }
        return new KpiComparisonDto(periodoActual, periodoAnterior, deltas);
    }

    static BigDecimal deltaPercent(BigDecimal actual, BigDecimal previo) {
        if (previo == null || previo.signum() == 0) {
            return null;
        }
        BigDecimal base = actual != null ? actual : BigDecimal.ZERO;
        return base.subtract(previo)
                .multiply(BigDecimal.valueOf(100))
                .divide(previo.abs(), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal valorSnapshot(KpiType kpi, KpiSnapshotDto s) {
        return switch (kpi) {
            case NEW_CLIENTS -> entero(s.getNewClients());
            case CLOSED_PROJECTS -> entero(s.getClosedProjects());
            case REVENUE -> decimal(s.getEstimatedRevenue());
            case ACTIVE_CAMPAIGNS -> entero(s.getActiveCampaigns());
            case CLIENTS_CONTACTED -> entero(s.getClientsContacted());
            case RESPONSE_RATE -> decimal(s.getResponseRate());
            case AVG_CLOSE_DAYS -> decimal(s.getAvgCloseDays());
            case PROJECTS_IN_PROGRESS -> entero(s.getProjectsInProgress());
        };
    }

    public List<ProjectAnalyticsDto> getProjects(String status, String clientId) {
        List<Project> proyectos = projectRepository.findAll().stream()
                .filter(p -> status == null || status.isBlank()
                        || (p.getStatus() != null && p.getStatus().equalsIgnoreCase(status.trim())))
                .filter(p -> clientId == null || clientId.isBlank() || clientId.equals(p.getClientId()))
                .sorted(Comparator.comparing(Project::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Set<String> ids = new HashSet<>();
        proyectos.forEach(p -> { if (p.getClientId() != null) ids.add(p.getClientId()); });
        Map<String, Client> clientes = cargarClientes(ids);

        Map<String, BigDecimal> aprobadoPorCliente = new HashMap<>();
        for (Proposal proposal : proposalRepository.findByStatus(Proposal.ProposalStatus.Approved)) {
            if (proposal.getClientId() == null || proposal.getEstimatedValue() == null) continue;
            aprobadoPorCliente.merge(proposal.getClientId(), proposal.getEstimatedValue(), BigDecimal::add);
        }

        return proyectos.stream()
                .map(p -> new ProjectAnalyticsDto(
                        p.getProjectId(),
                        p.getProjectName(),
                        p.getStatus(),
                        p.getClientId(),
                        p.getClientId() != null ? nombreCliente(clientes.get(p.getClientId()), p.getClientId()) : null,
                        p.getCreatedAt(),
                        p.getUpdatedAt(),
                        p.getClientId() != null
                                ? aprobadoPorCliente.getOrDefault(p.getClientId(), BigDecimal.ZERO)
                                : BigDecimal.ZERO))
                .toList();
    }

    private List<KpiDetailItemDto> itemsContacto(List<KpiSelection.ClientContact> contactos,
                                                 Map<String, Client> clientes, String singular, String plural) {
        return contactos.stream()
                .sorted(Comparator.comparing(KpiSelection.ClientContact::lastDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(c -> {
                    String nombre = nombreCliente(clientes.get(c.clientId()), c.clientId());
                    Client cliente = clientes.get(c.clientId());
                    return new KpiDetailItemDto(c.clientId(), nombre,
                            c.interactions() + " " + (c.interactions() == 1 ? singular : plural),
                            c.lastDate(),
                            BigDecimal.valueOf(c.interactions()),
                            cliente != null ? cliente.getPlan() : null,
                            c.clientId(), nombre);
                })
                .toList();
    }

    private KpiDetailItemDto itemProyecto(Project p, Map<String, Client> clientes, BigDecimal valor) {
        String nombre = p.getClientId() != null
                ? nombreCliente(clientes.get(p.getClientId()), p.getClientId())
                : "Sin cliente";
        return new KpiDetailItemDto(p.getProjectId(), p.getProjectName(), nombre,
                p.getUpdatedAt(), valor, p.getStatus(), p.getClientId(), nombre);
    }

    private static Collection<String> clientIdsDe(KpiType kpi, KpiSelection s) {
        Set<String> ids = new HashSet<>();
        switch (kpi) {
            case NEW_CLIENTS -> { }
            case CLOSED_PROJECTS -> s.closedProjects().forEach(p -> ids.add(p.getClientId()));
            case REVENUE -> s.approvedProposals().forEach(p -> ids.add(p.getClientId()));
            case ACTIVE_CAMPAIGNS -> s.activeCampaigns().forEach(c -> ids.add(c.getClientId()));
            case CLIENTS_CONTACTED -> s.contactedClients().forEach(c -> ids.add(c.clientId()));
            case RESPONSE_RATE -> s.respondedClients().forEach(c -> ids.add(c.clientId()));
            case AVG_CLOSE_DAYS -> s.closeTimes().forEach(t -> ids.add(t.project().getClientId()));
            case PROJECTS_IN_PROGRESS -> s.projectsInProgress().forEach(p -> ids.add(p.getClientId()));
        }
        ids.remove(null);
        return ids;
    }

    private Map<String, Client> cargarClientes(Collection<String> ids) {
        Map<String, Client> clientes = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return clientes;
        }
        List<Client> encontrados = clientRepository.findAllById(ids);
        if (encontrados != null) {
            encontrados.forEach(c -> clientes.put(c.getClientId(), c));
        }
        return clientes;
    }

    /** Cliente de la campaña, o "Segmento: <nombre>" / "Todos los clientes" si la audiencia no es un cliente. */
    private String audienciaCampana(Campaign c, Map<String, Client> clientes) {
        if (c.audienceOrDefault() != Campaign.AudienceType.CLIENT && audienceService != null) {
            return audienceService.describe(c, clientes);
        }
        return nombreCliente(clientes.get(c.getClientId()), c.getClientId());
    }

    static String nombreCliente(Client client, String fallbackId) {
        if (client != null) {
            if (client.getAdditionalInfo() != null && !client.getAdditionalInfo().isBlank()) {
                return client.getAdditionalInfo().trim();
            }
            if (client.getContactInfo() != null && !client.getContactInfo().isBlank()) {
                return client.getContactInfo().trim();
            }
        }
        return fallbackId;
    }

    private static String tituloPropuesta(Proposal p) {
        String descripcion = p.getDescription();
        if (descripcion == null || descripcion.isBlank()) {
            return "Propuesta #" + p.getProposalId();
        }
        String limpia = descripcion.trim();
        return limpia.length() > 120 ? limpia.substring(0, 117) + "..." : limpia;
    }

    private static BigDecimal entero(Integer valor) {
        return BigDecimal.valueOf(valor != null ? valor : 0);
    }

    private static BigDecimal decimal(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
