package com.cimaxis.demo.marketing.service.commercial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal.ProposalStatus;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.dto.commercial.FunnelClientResponse;
import com.cimaxis.demo.marketing.dto.commercial.FunnelResponse;
import com.cimaxis.demo.marketing.dto.commercial.FunnelStage;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Embudo comercial: conjuntos de clientes distintos por etapa.
 *
 * <ul>
 *   <li>clients: clientes registrados (con periodo: creados hasta el fin del mes; los que
 *       no tienen fecha de alta se cuentan).</li>
 *   <li>contacted / responded: mismas interacciones y definiciones que el KPI
 *       (KpiCalculationService.select usa findByContactDateBetween del mes y descarta
 *       SKIPPED_LIMIT), por eso coinciden con clientsContacted y responseRate.</li>
 *   <li>proposal_sent: propuesta Sent, In_negotiation, Approved o Rejected (con periodo:
 *       creada en el mes).</li>
 *   <li>approved: propuesta Approved (con periodo: fecha de respuesta en el mes, igual que
 *       el KPI de ingresos estimados).</li>
 *   <li>project: propuesta Approved con projectId (con periodo: respondida en el mes) o
 *       proyecto en PROJECTS (con periodo: creado en el mes).</li>
 * </ul>
 * Cada etapa se calcula con su propia definicion; no se fuerza que sea subconjunto de
 * la anterior, para no alterar los numeros que muestran los KPI.
 */
@Service
public class FunnelService {

    public static final String CLIENTS = "clients";
    public static final String CONTACTED = "contacted";
    public static final String RESPONDED = "responded";
    public static final String PROPOSAL_SENT = "proposal_sent";
    public static final String APPROVED = "approved";
    public static final String PROJECT = "project";

    private static final Map<String, String> ETAPAS = new LinkedHashMap<>();
    static {
        ETAPAS.put(CLIENTS, "Clientes");
        ETAPAS.put(CONTACTED, "Contactados");
        ETAPAS.put(RESPONDED, "Respondieron");
        ETAPAS.put(PROPOSAL_SENT, "Propuesta enviada");
        ETAPAS.put(APPROVED, "Aprobada");
        ETAPAS.put(PROJECT, "Con proyecto");
    }

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ClientRepository clientRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final ProposalRepository proposalRepository;
    private final ProjectRepository projectRepository;
    private final ClientScoreService scoreService;

    public FunnelService(ClientRepository clientRepository,
                         MarketingInteractionRepository interactionRepository,
                         ProposalRepository proposalRepository,
                         ProjectRepository projectRepository,
                         ClientScoreService scoreService) {
        this.clientRepository = clientRepository;
        this.interactionRepository = interactionRepository;
        this.proposalRepository = proposalRepository;
        this.projectRepository = projectRepository;
        this.scoreService = scoreService;
    }

    public FunnelResponse funnel(String period) {
        YearMonth ym = parsePeriod(period);
        Map<String, Set<String>> conjuntos = conjuntos(ym, clientRepository.findAll());

        List<FunnelStage> etapas = new ArrayList<>();
        ETAPAS.forEach((key, label) -> etapas.add(new FunnelStage(key, label, conjuntos.get(key).size())));

        List<Double> conversion = new ArrayList<>();
        for (int i = 0; i < etapas.size() - 1; i++) {
            conversion.add(porcentaje(etapas.get(i + 1).count(), etapas.get(i).count()));
        }
        return new FunnelResponse(ym != null ? ym.format(PERIOD_FORMAT) : null, etapas, conversion);
    }

    /** Clientes de una etapa, de mayor a menor puntaje. */
    public List<FunnelClientResponse> stageClients(String stageKey, String period) {
        String key = stageKey == null ? "" : stageKey.trim().toLowerCase();
        if (!ETAPAS.containsKey(key)) {
            throw new IllegalArgumentException("Etapa inválida: " + stageKey
                    + ". Valores permitidos: " + ETAPAS.keySet());
        }
        YearMonth ym = parsePeriod(period);
        List<Client> clientes = nullSafe(clientRepository.findAll());
        Set<String> ids = conjuntos(ym, clientes).get(key);

        Map<String, Client> porId = new HashMap<>();
        for (Client c : clientes) porId.put(c.getClientId(), c);
        CommercialFacts hechos = scoreService.hechos();
        LocalDate hoy = scoreService.hoy();

        return ids.stream()
                .map(id -> {
                    Client c = porId.get(id);
                    CommercialFacts.ClientFacts h = hechos.of(id);
                    return new FunnelClientResponse(id, CommercialFacts.nombre(c),
                            c != null ? c.getPlan() : null, h.lastContact(), InterestScore.calcular(c, h, hoy));
                })
                .sorted(Comparator.comparingInt(FunnelClientResponse::score).reversed()
                        .thenComparing(FunnelClientResponse::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<String, Set<String>> conjuntos(YearMonth ym, List<Client> clientes) {
        LocalDateTime desde = ym != null ? ym.atDay(1).atStartOfDay() : null;
        LocalDateTime hasta = ym != null ? ym.atEndOfMonth().atTime(23, 59, 59) : null;
        LocalDate desdeFecha = ym != null ? ym.atDay(1) : null;
        LocalDate hastaFecha = ym != null ? ym.atEndOfMonth() : null;

        Set<String> registrados = new LinkedHashSet<>();
        for (Client c : nullSafe(clientes)) {
            if (c.getClientId() == null) continue;
            if (ym == null || c.getCreatedAt() == null || !c.getCreatedAt().isAfter(hasta)) {
                registrados.add(c.getClientId());
            }
        }

        // Mismo filtro de fechas que KpiCalculationService.select.
        List<MarketingInteraction> interacciones = ym != null
                ? interactionRepository.findByContactDateBetween(desde, hasta)
                : interactionRepository.findAll();

        Set<String> enviada = new LinkedHashSet<>();
        Set<String> aprobada = new LinkedHashSet<>();
        Set<String> conProyecto = new LinkedHashSet<>();
        for (Proposal p : nullSafe(proposalRepository.findAll())) {
            if (p.getClientId() == null || p.getStatus() == null) continue;
            if (CommercialFacts.ESTADOS_ENVIADA.contains(p.getStatus())
                    && (ym == null || entre(p.getCreatedDate(), desdeFecha, hastaFecha))) {
                enviada.add(p.getClientId());
            }
            if (p.getStatus() == ProposalStatus.Approved
                    && (ym == null || entre(p.getResponseDate(), desdeFecha, hastaFecha))) {
                aprobada.add(p.getClientId());
                if (CommercialFacts.tieneProyectoVinculado(p)) conProyecto.add(p.getClientId());
            }
        }
        for (Project project : nullSafe(projectRepository.findAll())) {
            if (project.getClientId() == null) continue;
            if (ym == null || entre(project.getCreatedAt(), desde, hasta)) {
                conProyecto.add(project.getClientId());
            }
        }

        Map<String, Set<String>> resultado = new LinkedHashMap<>();
        resultado.put(CLIENTS, registrados);
        resultado.put(CONTACTED, KpiCalculationService.clientesContactados(interacciones));
        resultado.put(RESPONDED, KpiCalculationService.clientesQueRespondieron(interacciones));
        resultado.put(PROPOSAL_SENT, enviada);
        resultado.put(APPROVED, aprobada);
        resultado.put(PROJECT, conProyecto);
        return resultado;
    }

    /** count[i+1] / count[i] x 100 con dos decimales; null si count[i] es 0. */
    static Double porcentaje(long siguiente, long actual) {
        if (actual == 0) return null;
        return BigDecimal.valueOf(siguiente).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(actual), 2, RoundingMode.HALF_UP).doubleValue();
    }

    /** null = acumulado historico. */
    static YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) return null;
        try {
            return YearMonth.parse(period.trim(), PERIOD_FORMAT);
        } catch (Exception e) {
            throw new IllegalArgumentException("Periodo inválido: " + period + ". Formato esperado YYYY-MM");
        }
    }

    private static boolean entre(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    private static boolean entre(LocalDateTime fecha, LocalDateTime desde, LocalDateTime hasta) {
        return fecha != null && !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    private static <T> List<T> nullSafe(List<T> lista) {
        return lista != null ? lista : List.of();
    }
}
