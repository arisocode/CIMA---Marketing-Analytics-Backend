package com.cimaxis.demo.analytics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.cimaxis.demo.marketing.service.notifications.DeliveryStatus;

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
 *  6. response_rate        - clientes que respondieron / clientes contactados * 100
 *  7. avg_close_days       - dias promedio entre alta del cliente y cierre
 *  8. projects_in_progress - proyectos en curso al cierre del periodo
 *
 * El numero de cada KPI se deriva de los registros de select(period), los mismos
 * que lista KpiDetailService, para que la tarjeta y su detalle coincidan siempre.
 */
@Service
public class KpiCalculationService {

    private static final Logger log = LoggerFactory.getLogger(KpiCalculationService.class);

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final Set<String> ESTADOS_CERRADO =
            Set.of("completed", "closed", "finalizado", "completado", "cerrado");
    private static final Set<String> ESTADOS_EN_CURSO =
            Set.of("in_progress", "in_review", "in progress", "active",
                    "en progreso", "activo", "en curso");

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
        return toSnapshot(select(period));
    }

    public KpiSelection select(String period) {
        YearMonth ym = resolvePeriod(period);
        LocalDateTime desde = ym.atDay(1).atStartOfDay();
        LocalDateTime hasta = ym.atEndOfMonth().atTime(23, 59, 59);
        LocalDate desdeFecha = ym.atDay(1);
        LocalDate hastaFecha = ym.atEndOfMonth();

        List<Client> clientesNuevos = clientRepository.findByCreatedAtBetween(desde, hasta);

        List<Project> cerradosEnPeriodo =
                projectRepository.findByStatusInAndUpdatedAtBetween(ESTADOS_CERRADO, desde, hasta);

        List<Proposal> aprobadas = proposalRepository.findByStatusAndResponseDateBetween(
                Proposal.ProposalStatus.Approved, desdeFecha, hastaFecha);

        List<Campaign> campanasActivas = campaignRepository.findActiveInPeriod(
                Campaign.CampaignStatus.Active, desdeFecha, hastaFecha);

        List<MarketingInteraction> interacciones =
                interactionRepository.findByContactDateBetween(desde, hasta);

        List<Project> enCurso = projectRepository.findByStatusIn(ESTADOS_EN_CURSO);

        return new KpiSelection(
                ym,
                nullSafe(clientesNuevos),
                nullSafe(cerradosEnPeriodo),
                nullSafe(aprobadas),
                nullSafe(campanasActivas),
                resumirPorCliente(nullSafe(interacciones), false),
                resumirPorCliente(nullSafe(interacciones), true),
                calcularTiemposDeCierre(nullSafe(cerradosEnPeriodo)),
                nullSafe(enCurso));
    }

    private KpiSnapshot toSnapshot(KpiSelection seleccion) {
        return KpiSnapshot.builder()
                .period(seleccion.period().format(PERIOD_FORMAT))
                .calculatedAt(LocalDateTime.now())
                .newClients(seleccion.newClients().size())
                .closedProjects(seleccion.closedProjects().size())
                .estimatedRevenue(seleccion.estimatedRevenue())
                .activeCampaigns(seleccion.activeCampaigns().size())
                .clientsContacted(seleccion.contactedClients().size())
                .responseRate(seleccion.responseRate())
                .avgCloseDays(seleccion.avgCloseDays())
                .projectsInProgress(seleccion.projectsInProgress().size())
                .build();
    }

    /**
     * Contacto: interaccion con cliente que no fue omitida por el limite diario de
     * correos (SKIPPED_LIMIT nunca salio). Es la misma definicion que usan el KPI
     * de clientes contactados, el embudo comercial y el puntaje de interes.
     */
    public static boolean esContacto(MarketingInteraction interaction) {
        return interaction != null
                && interaction.getClientId() != null
                && !DeliveryStatus.SKIPPED_LIMIT.name().equals(interaction.getDeliveryStatus());
    }

    /** Clientes distintos con al menos un contacto en la lista (orden de aparicion). */
    public static Set<String> clientesContactados(List<MarketingInteraction> interacciones) {
        Set<String> ids = new LinkedHashSet<>();
        for (MarketingInteraction interaction : nullSafe(interacciones)) {
            if (esContacto(interaction)) ids.add(interaction.getClientId());
        }
        return ids;
    }

    /** Clientes distintos con al menos una respuesta (contacto que ademas es respuesta). */
    public static Set<String> clientesQueRespondieron(List<MarketingInteraction> interacciones) {
        Set<String> ids = new LinkedHashSet<>();
        for (MarketingInteraction interaction : nullSafe(interacciones)) {
            if (esContacto(interaction) && esRespuesta(interaction)) ids.add(interaction.getClientId());
        }
        return ids;
    }

    /** Respuesta: texto de respuesta registrado o tipo click, inquiry, purchase o testimonial. */
    public static boolean esRespuesta(MarketingInteraction interaction) {
        return interaction.getResponse() != null
                || (interaction.getInteractionType() != null
                    && TIPOS_RESPUESTA.contains(interaction.getInteractionType()));
    }

    private List<KpiSelection.ClientContact> resumirPorCliente(List<MarketingInteraction> interacciones,
                                                               boolean soloRespuestas) {
        Map<String, LocalDateTime> ultima = new LinkedHashMap<>();
        Map<String, Long> cantidad = new LinkedHashMap<>();
        for (MarketingInteraction interaction : interacciones) {
            // Un correo omitido por el limite diario nunca salio: no es un contacto.
            if (!esContacto(interaction)) continue;
            if (soloRespuestas && !esRespuesta(interaction)) continue;
            String clientId = interaction.getClientId();
            cantidad.merge(clientId, 1L, Long::sum);
            LocalDateTime fecha = interaction.getContactDate();
            LocalDateTime previa = ultima.get(clientId);
            if (previa == null || (fecha != null && fecha.isAfter(previa))) {
                ultima.put(clientId, fecha);
            }
        }
        List<KpiSelection.ClientContact> resultado = new ArrayList<>();
        cantidad.forEach((clientId, total) ->
                resultado.add(new KpiSelection.ClientContact(clientId, ultima.get(clientId), total)));
        return resultado;
    }

    /** Dias entre el alta del cliente y el cierre; omite proyectos sin cliente o sin fechas. */
    private List<KpiSelection.ProjectCloseTime> calcularTiemposDeCierre(List<Project> proyectosCerrados) {
        List<String> clientIds = proyectosCerrados.stream()
                .map(Project::getClientId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<String, Client> clientes = new HashMap<>();
        if (!clientIds.isEmpty()) {
            for (Client client : nullSafe(clientRepository.findAllById(clientIds))) {
                clientes.put(client.getClientId(), client);
            }
        }

        List<KpiSelection.ProjectCloseTime> tiempos = new ArrayList<>();
        for (Project project : proyectosCerrados) {
            if (project.getUpdatedAt() == null || project.getClientId() == null) continue;

            Client client = clientes.get(project.getClientId());
            if (client == null || client.getCreatedAt() == null) continue;

            long dias = ChronoUnit.DAYS.between(client.getCreatedAt(), project.getUpdatedAt());
            if (dias < 0) continue;

            tiempos.add(new KpiSelection.ProjectCloseTime(project, client, dias));
        }
        return tiempos;
    }

    private static <T> List<T> nullSafe(List<T> lista) {
        return lista != null ? lista : List.of();
    }

    /** Periodo YYYY-MM; si viene vacio se usa el mes en curso. */
    public YearMonth resolvePeriod(String period) {
        return parsePeriod(period);
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
