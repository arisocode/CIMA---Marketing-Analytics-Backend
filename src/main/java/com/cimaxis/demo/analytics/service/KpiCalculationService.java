package com.cimaxis.demo.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.KpiSnapshot;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.dto.KpiSnapshotDto;
import com.cimaxis.demo.analytics.mapper.KpiSnapshotMapper;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.KpiSnapshotRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;

/**
 * Calcula los ocho KPIs definidos y los persiste en
 * KPI_SNAPSHOTS.
 *
 * KPIs calculados:
 *  1. new_clients          - clientes nuevos en el periodo
 *  2. closed_projects      - proyectos cerrados en el periodo
 *  3. estimated_revenue    - valor de propuestas aprobadas en el periodo
 *  4. active_campaigns     - campanas vigentes durante el periodo
 *  5. clients_contacted    - clientes distintos contactados
 *  6. response_rate        - respuestas / clientes contactados * 100
 *  7. avg_close_days       - dias promedio entre alta del cliente y cierre
 *  8. projects_in_progress - proyectos en curso al cierre del periodo
 */
@Service
public class KpiCalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiCalculationService.class);

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final Set<String> ESTADOS_CERRADO =
            Set.of("completed", "closed", "finalizado", "completado", "cerrado");
    private static final Set<String> ESTADOS_EN_CURSO =
            Set.of("in progress", "active", "en progreso", "activo", "en curso");

    /** Tipos de interaccion que se cuentan como respuesta del cliente. */
    private static final List<MarketingInteraction.InteractionType> TIPOS_RESPUESTA = List.of(
            MarketingInteraction.InteractionType.click,
            MarketingInteraction.InteractionType.inquiry,
            MarketingInteraction.InteractionType.purchase,
            MarketingInteraction.InteractionType.testimonial);

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final ProposalRepository proposalRepository;
    private final CampaignRepository campaignRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final KpiSnapshotRepository kpiSnapshotRepository;
    private final KpiSnapshotMapper kpiSnapshotMapper;

    public KpiCalculationService(ClientRepository clientRepository,
                                 ProjectRepository projectRepository,
                                 ProposalRepository proposalRepository,
                                 CampaignRepository campaignRepository,
                                 MarketingInteractionRepository interactionRepository,
                                 KpiSnapshotRepository kpiSnapshotRepository,
                                 KpiSnapshotMapper kpiSnapshotMapper) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.proposalRepository = proposalRepository;
        this.campaignRepository = campaignRepository;
        this.interactionRepository = interactionRepository;
        this.kpiSnapshotRepository = kpiSnapshotRepository;
        this.kpiSnapshotMapper = kpiSnapshotMapper;
    }

    public KpiSnapshotDto calculate(String period) {
        return kpiSnapshotMapper.toDto(calcularSnapshot(period));
    }

    @Transactional
    public KpiSnapshotDto calculateAndStore(String period, String calculatedBy) {
        KpiSnapshot calculado = calcularSnapshot(period);

        KpiSnapshot destino = kpiSnapshotRepository.findByPeriod(calculado.getPeriod())
                .orElseGet(KpiSnapshot::new);

        kpiSnapshotMapper.copiarValores(calculado, destino);
        destino.setCalculatedAt(LocalDateTime.now());
        destino.setCalculatedBy(calculatedBy);

        KpiSnapshot guardado = kpiSnapshotRepository.save(destino);
        log.info("Snapshot de KPIs almacenado para el periodo {}", guardado.getPeriod());
        return kpiSnapshotMapper.toDto(guardado);
    }

    @Scheduled(cron = "${cimaxis.kpi.cron:0 30 1 * * *}")
    public void consolidarPeriodoActual() {
        try {
            calculateAndStore(YearMonth.now().format(PERIOD_FORMAT), null);
        } catch (Exception e) {
            log.error("Error consolidando KPIs del periodo actual: {}", e.getMessage());
        }
    }

    private KpiSnapshot calcularSnapshot(String period) {
        YearMonth ym = parsePeriod(period);
        LocalDateTime desde = ym.atDay(1).atStartOfDay();
        LocalDateTime hasta = ym.atEndOfMonth().atTime(23, 59, 59);
        LocalDate desdeFecha = ym.atDay(1);
        LocalDate hastaFecha = ym.atEndOfMonth();

        long clientesNuevos = clientRepository.countByCreatedAtBetween(desde, hasta);

        List<Project> cerradosEnPeriodo =
                projectRepository.findByStatusInAndUpdatedAtBetween(ESTADOS_CERRADO, desde, hasta);
        long proyectosCerrados = cerradosEnPeriodo.size();

        BigDecimal ingresos = proposalRepository.sumValueByStatusBetween(
                Proposal.ProposalStatus.Approved, desdeFecha, hastaFecha);
        if (ingresos == null) {
            ingresos = BigDecimal.ZERO;
        }

        long campanasActivas = campaignRepository.countActiveInPeriod(
                Campaign.CampaignStatus.Active, desdeFecha, hastaFecha);

        long clientesContactados =
                interactionRepository.countDistinctClientsContactedBetween(desde, hasta);

        long respuestas = interactionRepository.countResponsesBetween(desde, hasta, TIPOS_RESPUESTA);

        BigDecimal tasaRespuesta = clientesContactados == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(respuestas)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(clientesContactados), 2, RoundingMode.HALF_UP);

        BigDecimal diasPromedioCierre = calcularDiasPromedioCierre(cerradosEnPeriodo);

        long proyectosEnCurso = projectRepository.countByStatusIn(ESTADOS_EN_CURSO);

        return KpiSnapshot.builder()
                .period(ym.format(PERIOD_FORMAT))
                .calculatedAt(LocalDateTime.now())
                .newClients((int) clientesNuevos)
                .closedProjects((int) proyectosCerrados)
                .estimatedRevenue(ingresos)
                .activeCampaigns((int) campanasActivas)
                .clientsContacted((int) clientesContactados)
                .responseRate(tasaRespuesta)
                .avgCloseDays(diasPromedioCierre)
                .projectsInProgress((int) proyectosEnCurso)
                .build();
    }

    private BigDecimal calcularDiasPromedioCierre(List<Project> proyectosCerrados) {
        long acumulado = 0;
        int considerados = 0;

        for (Project project : proyectosCerrados) {
            if (project.getUpdatedAt() == null || project.getClientId() == null) continue;

            Optional<Client> client = clientRepository.findById(project.getClientId());
            if (client.isEmpty() || client.get().getCreatedAt() == null) continue;

            long dias = ChronoUnit.DAYS.between(
                    client.get().getCreatedAt(), project.getUpdatedAt());
            if (dias < 0) continue;

            acumulado += dias;
            considerados++;
        }

        return considerados == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(acumulado)
                .divide(BigDecimal.valueOf(considerados), 2, RoundingMode.HALF_UP);
    }

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period, PERIOD_FORMAT);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Periodo invalido: " + period + ". Formato esperado YYYY-MM");
        }
    }
}
