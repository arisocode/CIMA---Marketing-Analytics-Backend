package com.cimaxis.demo.marketing.service.workflows;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.analytics.service.KpiCalculationService;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;
import com.cimaxis.demo.marketing.service.campaigns.CampaignAudienceService;
import com.cimaxis.demo.marketing.service.commercial.CommercialFacts;

/**
 * Unica fuente de verdad de "a quien le toca hoy" una automatizacion. La usan el
 * planificador, la vista previa (GET /workflows/{id}/preview) y "ejecutar ahora"
 * (POST /workflows/{id}/run-now), asi que los tres siempre coinciden.
 *
 * <p>Reglas, en este orden:</p>
 * <ol>
 *   <li>La automatizacion debe estar activa y su campaña Active con hoy entre sus fechas.</li>
 *   <li>Solo se evalua la audiencia de la campaña (CLIENT, SEGMENT o ALL).</li>
 *   <li>manual: el planificador nunca lo corre; "ejecutar ahora" lo corre para toda la audiencia.</li>
 *   <li>scheduled_date: solo cuando ahora (America/Bogota) &gt;= scheduledAt.</li>
 *   <li>Historial del cliente con ESTE workflow:
 *     scheduled_date y project_completed llegan una sola vez; no_contact_x_days y
 *     proposal_no_response se repiten si el ultimo envio exitoso tiene mas de N dias;
 *     maximo de intentos fallidos; espera tras un fallo; y el envio omitido por el cupo
 *     diario de correo se reintenta al dia siguiente.</li>
 *   <li>Condicion del disparador: no_contact_x_days = ultimo contacto hace mas de N dias
 *     (o nunca); proposal_no_response = propuesta Sent o In_negotiation sin respuesta
 *     creada hace N dias o mas; project_completed = algun proyecto completado.</li>
 * </ol>
 * Contacto = KpiCalculationService.esContacto (sin SKIPPED_LIMIT ni WhatsApp pendientes).
 */
@Service
public class WorkflowEligibilityService {

    public static final ZoneId ZONA_CIMA = ZoneId.of("America/Bogota");
    private static final Set<String> ESTADOS_PROYECTO_CERRADO =
            Set.of("completed", "closed", "finalizado", "completado", "cerrado");
    private static final String[] MESES_CORTOS = {"ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.",
            "ago.", "sept.", "oct.", "nov.", "dic."};

    /** Quien evalua: el planificador nunca corre los manual; "ejecutar ahora" si. */
    public enum Mode { SCHEDULER, RUN_NOW }

    public record Candidate(String clientId, String name, String plan) {
    }

    public record Skip(String clientId, String name, String reason) {
    }

    public record Evaluation(Workflow workflow, Campaign campaign, boolean campaignActive,
                             Campaign.AudienceType audienceType, int audienceSize, LocalDateTime nextRunAt,
                             List<Candidate> eligible, List<Skip> skipped) {
    }

    private final CampaignRepository campaignRepository;
    private final CampaignAudienceService audienceService;
    private final ClientRepository clientRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final ProposalRepository proposalRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final Clock clock;
    /** Zona en la que se guardan executedAt y contactDate (LocalDateTime.now() del servidor). */
    private final ZoneId zonaAlmacenamiento;
    private final long retryDelayMinutes;
    private final long maxDeliveryAttempts;
    private final int defaultNoContactDays;

    @Autowired
    public WorkflowEligibilityService(CampaignRepository campaignRepository,
                                      CampaignAudienceService audienceService,
                                      ClientRepository clientRepository,
                                      MarketingInteractionRepository interactionRepository,
                                      ProposalRepository proposalRepository,
                                      ProjectRepository projectRepository,
                                      WorkflowExecutionRepository executionRepository,
                                      @Value("${cimaxis.scheduler.retry-delay-minutes:60}") long retryDelayMinutes,
                                      @Value("${cimaxis.scheduler.max-delivery-attempts:3}") long maxDeliveryAttempts,
                                      @Value("${cimaxis.scheduler.default-no-contact-days:15}") int defaultNoContactDays) {
        this(campaignRepository, audienceService, clientRepository, interactionRepository, proposalRepository,
                projectRepository, executionRepository, retryDelayMinutes, maxDeliveryAttempts, defaultNoContactDays,
                Clock.system(ZONA_CIMA), ZoneId.systemDefault());
    }

    WorkflowEligibilityService(CampaignRepository campaignRepository,
                               CampaignAudienceService audienceService,
                               ClientRepository clientRepository,
                               MarketingInteractionRepository interactionRepository,
                               ProposalRepository proposalRepository,
                               ProjectRepository projectRepository,
                               WorkflowExecutionRepository executionRepository,
                               long retryDelayMinutes,
                               long maxDeliveryAttempts,
                               int defaultNoContactDays,
                               Clock clock,
                               ZoneId zonaAlmacenamiento) {
        this.campaignRepository = campaignRepository;
        this.audienceService = audienceService;
        this.clientRepository = clientRepository;
        this.interactionRepository = interactionRepository;
        this.proposalRepository = proposalRepository;
        this.projectRepository = projectRepository;
        this.executionRepository = executionRepository;
        this.retryDelayMinutes = retryDelayMinutes;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
        this.defaultNoContactDays = defaultNoContactDays;
        this.clock = clock;
        this.zonaAlmacenamiento = zonaAlmacenamiento;
    }

    public Evaluation evaluate(Workflow workflow, Mode mode) {
        LocalDateTime ahora = LocalDateTime.now(clock.withZone(ZONA_CIMA));
        LocalDate hoy = ahora.toLocalDate();
        Workflow.TriggerType disparador = workflow.getTriggerType();
        LocalDateTime proxima = disparador == Workflow.TriggerType.scheduled_date ? workflow.getScheduledAt() : null;

        Campaign campana = workflow.getCampaignId() == null ? null
                : campaignRepository.findById(workflow.getCampaignId()).orElse(null);
        if (campana == null) {
            return new Evaluation(workflow, null, false, null, 0, proxima, List.of(), List.of());
        }
        boolean vigente = CampaignAudienceService.vigente(campana, hoy);
        List<String> audiencia = audienceService.resolve(campana);
        Map<String, Client> clientes = new HashMap<>();
        if (!audiencia.isEmpty()) {
            List<Client> encontrados = clientRepository.findAllById(audiencia);
            if (encontrados != null) encontrados.forEach(c -> clientes.put(c.getClientId(), c));
        }

        List<Candidate> elegibles = new ArrayList<>();
        List<Skip> omitidos = new ArrayList<>();

        String motivoGeneral = motivoGeneral(workflow, campana, vigente, mode, ahora);
        if (motivoGeneral != null) {
            for (String id : audiencia) omitidos.add(new Skip(id, nombre(clientes.get(id)), motivoGeneral));
            return new Evaluation(workflow, campana, vigente, campana.audienceOrDefault(), audiencia.size(), proxima,
                    elegibles, omitidos);
        }

        int umbral = umbral(workflow);
        Map<String, List<WorkflowExecution>> historial = historialPorCliente(workflow);
        Map<String, LocalDateTime> ultimoContacto = disparador == Workflow.TriggerType.no_contact_x_days
                ? ultimosContactos(new HashSet<>(audiencia)) : Map.of();
        Map<String, List<Proposal>> propuestasAbiertas = disparador == Workflow.TriggerType.proposal_no_response
                ? propuestasAbiertas() : Map.of();
        Set<String> conProyectoCerrado = disparador == Workflow.TriggerType.project_completed
                ? clientesConProyectoCerrado() : Set.of();

        for (String clientId : audiencia) {
            Client c = clientes.get(clientId);
            String motivo = motivoPorHistorial(disparador, historial.getOrDefault(clientId, List.of()), ahora, umbral);
            if (motivo == null) {
                motivo = switch (disparador) {
                    case no_contact_x_days -> motivoSinContacto(ultimoContacto.get(clientId), hoy, umbral);
                    case proposal_no_response -> motivoPropuesta(propuestasAbiertas.get(clientId), hoy, umbral);
                    case project_completed -> conProyectoCerrado.contains(clientId) ? null : "Sin proyecto completado";
                    case scheduled_date, manual -> null;
                };
            }
            if (motivo == null) {
                elegibles.add(new Candidate(clientId, nombre(c), c != null ? c.getPlan() : null));
            } else {
                omitidos.add(new Skip(clientId, nombre(c), motivo));
            }
        }
        return new Evaluation(workflow, campana, vigente, campana.audienceOrDefault(), audiencia.size(), proxima,
                elegibles, omitidos);
    }

    // ------------------------------------------------------------------ reglas

    private String motivoGeneral(Workflow w, Campaign c, boolean vigente, Mode mode, LocalDateTime ahora) {
        if (!Boolean.TRUE.equals(w.getActive())) return "La automatización está inactiva";
        if (!vigente) {
            if (c.getStatus() != Campaign.CampaignStatus.Active) return "La campaña no está activa";
            return "La campaña está fuera de sus fechas (" + fechaCorta(c.getStartDate())
                    + (c.getEndDate() != null ? " al " + fechaCorta(c.getEndDate()) : "") + ")";
        }
        if (w.getTriggerType() == Workflow.TriggerType.manual && mode == Mode.SCHEDULER) {
            return "Disparador manual: solo se ejecuta con «Ejecutar ahora»";
        }
        if (w.getTriggerType() == Workflow.TriggerType.scheduled_date) {
            if (w.getScheduledAt() == null) return "Falta la fecha programada";
            if (ahora.isBefore(w.getScheduledAt())) {
                return "Aún no llega la fecha programada (" + fechaHoraCorta(w.getScheduledAt()) + ")";
            }
        }
        return null;
    }

    /** Envios previos de ESTE workflow al cliente: una vez, repeticion, reintentos y cupo. */
    private String motivoPorHistorial(Workflow.TriggerType disparador, List<WorkflowExecution> historial,
                                      LocalDateTime ahora, int umbral) {
        if (historial.isEmpty()) return null;
        WorkflowExecution ultimoExito = null;
        for (WorkflowExecution e : historial) {
            if (e.getResult() == WorkflowExecution.ExecutionResult.success) ultimoExito = e;
        }
        if (ultimoExito != null) {
            LocalDateTime cuando = aBogota(ultimoExito.getExecutedAt());
            switch (disparador) {
                case scheduled_date, project_completed -> {
                    return "Ya lo recibió el " + diaMes(cuando);
                }
                case no_contact_x_days, proposal_no_response -> {
                    if (cuando != null && ChronoUnit.DAYS.between(cuando.toLocalDate(), ahora.toLocalDate()) <= umbral) {
                        return "Ya lo recibió el " + diaMes(cuando) + " (se repite cada " + umbral + " días)";
                    }
                }
                case manual -> { }
            }
        }

        final WorkflowExecution exito = ultimoExito;
        long fallidos = historial.stream()
                .filter(e -> e.getResult() == WorkflowExecution.ExecutionResult.failed)
                .filter(e -> exito == null || despues(e.getExecutedAt(), exito.getExecutedAt()))
                .count();
        if (fallidos >= maxDeliveryAttempts) {
            return "Superó los reintentos (" + fallidos + " intentos fallidos)";
        }

        WorkflowExecution ultima = historial.get(historial.size() - 1);
        LocalDateTime ultimaEn = aBogota(ultima.getExecutedAt());
        if (ultima.getResult() == WorkflowExecution.ExecutionResult.pending && ultimaEn != null
                && ultimaEn.toLocalDate().equals(ahora.toLocalDate())) {
            return "En espera por el cupo diario de correo; se reintenta mañana";
        }
        if (ultima.getResult() == WorkflowExecution.ExecutionResult.failed && ultimaEn != null
                && ultimaEn.plusMinutes(retryDelayMinutes).isAfter(ahora)) {
            return "Falló el último intento; se reintenta desde las " + hora(ultimaEn.plusMinutes(retryDelayMinutes));
        }
        return null;
    }

    private static String motivoSinContacto(LocalDateTime ultimoContacto, LocalDate hoy, int umbral) {
        if (ultimoContacto == null) return null;
        long dias = ChronoUnit.DAYS.between(ultimoContacto.toLocalDate(), hoy);
        if (dias > umbral) return null;
        return (dias <= 0 ? "Contactado hoy" : dias == 1 ? "Contactado hace 1 día" : "Contactado hace " + dias + " días")
                + " (umbral " + umbral + ")";
    }

    private static String motivoPropuesta(List<Proposal> abiertas, LocalDate hoy, int umbral) {
        if (abiertas == null || abiertas.isEmpty()) return "Sin propuesta pendiente";
        LocalDate limite = hoy.minusDays(umbral);
        boolean alguna = abiertas.stream().anyMatch(p -> p.getCreatedDate() != null && !p.getCreatedDate().isAfter(limite));
        if (alguna) return null;
        LocalDate masReciente = abiertas.stream().map(Proposal::getCreatedDate).filter(d -> d != null)
                .max(Comparator.naturalOrder()).orElse(hoy);
        long dias = ChronoUnit.DAYS.between(masReciente, hoy);
        return (dias <= 0 ? "Propuesta enviada hoy" : dias == 1 ? "Propuesta enviada hace 1 día"
                : "Propuesta enviada hace " + dias + " días") + " (umbral " + umbral + ")";
    }

    // ------------------------------------------------------------------ datos

    private int umbral(Workflow w) {
        return w.getNoContactDays() != null && w.getNoContactDays() > 0 ? w.getNoContactDays() : defaultNoContactDays;
    }

    private Map<String, List<WorkflowExecution>> historialPorCliente(Workflow w) {
        Map<String, List<WorkflowExecution>> porCliente = new HashMap<>();
        if (w.getWorkflowId() == null) return porCliente;
        List<WorkflowExecution> ejecuciones = executionRepository.findByWorkflowId(w.getWorkflowId());
        if (ejecuciones == null) return porCliente;
        ejecuciones.stream()
                .filter(e -> e.getClientId() != null)
                .sorted(Comparator.comparing(WorkflowExecution::getExecutedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(e -> porCliente.computeIfAbsent(e.getClientId(), k -> new ArrayList<>()).add(e));
        return porCliente;
    }

    /** Ultimo contacto (en hora de Bogota) de cada cliente de la audiencia. */
    private Map<String, LocalDateTime> ultimosContactos(Set<String> audiencia) {
        Map<String, LocalDateTime> ultimos = new HashMap<>();
        List<MarketingInteraction> interacciones = interactionRepository.findAll();
        if (interacciones == null) return ultimos;
        for (MarketingInteraction i : interacciones) {
            if (!audiencia.contains(i.getClientId()) || !KpiCalculationService.esContacto(i)) continue;
            LocalDateTime fecha = aBogota(i.getContactDate());
            if (fecha == null) continue;
            ultimos.merge(i.getClientId(), fecha, (a, b) -> a.isAfter(b) ? a : b);
        }
        return ultimos;
    }

    private Map<String, List<Proposal>> propuestasAbiertas() {
        Map<String, List<Proposal>> porCliente = new HashMap<>();
        List<Proposal> propuestas = proposalRepository.findAll();
        if (propuestas == null) return porCliente;
        for (Proposal p : propuestas) {
            if (p.getClientId() == null || p.getResponseDate() != null) continue;
            if (!CommercialFacts.ESTADOS_ABIERTA.contains(p.getStatus())) continue;
            porCliente.computeIfAbsent(p.getClientId(), k -> new ArrayList<>()).add(p);
        }
        return porCliente;
    }

    private Set<String> clientesConProyectoCerrado() {
        Set<String> ids = new HashSet<>();
        List<Project> proyectos = projectRepository.findAll();
        if (proyectos == null) return ids;
        for (Project p : proyectos) {
            String estado = p.getStatus() != null ? p.getStatus().toLowerCase().replace("_", " ").trim() : "";
            if (p.getClientId() != null && ESTADOS_PROYECTO_CERRADO.contains(estado)) ids.add(p.getClientId());
        }
        return ids;
    }

    // ------------------------------------------------------------------ formato

    private LocalDateTime aBogota(LocalDateTime almacenado) {
        if (almacenado == null) return null;
        return almacenado.atZone(zonaAlmacenamiento).withZoneSameInstant(ZONA_CIMA).toLocalDateTime();
    }

    private static boolean despues(LocalDateTime a, LocalDateTime b) {
        if (a == null) return false;
        return b == null || a.isAfter(b);
    }

    private static String nombre(Client c) {
        return CommercialFacts.nombre(c);
    }

    /** "20/10". */
    static String diaMes(LocalDateTime fecha) {
        return fecha == null ? "—" : String.format("%02d/%02d", fecha.getDayOfMonth(), fecha.getMonthValue());
    }

    /** "1 dic.". */
    static String fechaCorta(LocalDate fecha) {
        return fecha == null ? "—" : fecha.getDayOfMonth() + " " + MESES_CORTOS[fecha.getMonthValue() - 1];
    }

    /** "1 dic. 9:00 a. m.". */
    static String fechaHoraCorta(LocalDateTime fecha) {
        return fechaCorta(fecha.toLocalDate()) + " " + hora(fecha);
    }

    /** "9:00 a. m.", "3:15 p. m.". */
    static String hora(LocalDateTime fecha) {
        int h = fecha.getHour();
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + String.format("%02d", fecha.getMinute()) + (h < 12 ? " a. m." : " p. m.");
    }
}
