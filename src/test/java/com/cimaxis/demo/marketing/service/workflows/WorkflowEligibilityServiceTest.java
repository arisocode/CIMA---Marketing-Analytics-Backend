package com.cimaxis.demo.marketing.service.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowAudienceClient;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutedClient;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowPreviewResponse;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowRunNowResponse;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowExecutionMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowExecutionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;
import com.cimaxis.demo.marketing.service.campaigns.CampaignAudienceService;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;

/**
 * Reglas de los disparadores en el servicio unico que usan el planificador, la vista
 * previa y "ejecutar ahora". Ahora = 21 de octubre de 2026, 10:00 a. m. en Bogota.
 * Audiencia de la campaña: c1, c2, c3 y c4.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowEligibilityServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ZoneOffset.UTC);
    private static final int WORKFLOW_ID = 5;

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignAudienceService audienceService;
    @Mock private ClientRepository clientRepository;
    @Mock private MarketingInteractionRepository interactionRepository;
    @Mock private ProposalRepository proposalRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private WorkflowExecutionRepository executionRepository;
    @Mock private WorkflowRepository workflowRepository;
    @Mock private WorkflowExecutionService executionService;

    private WorkflowEligibilityService service;
    private Campaign campana;
    private List<WorkflowExecution> historial;
    private List<MarketingInteraction> interacciones;
    private List<Proposal> propuestas;
    private List<Project> proyectos;
    private List<String> ejecutadosPorRunFor;

    @BeforeEach
    void setUp() {
        campana = Campaign.builder().campaignId(20).campaignName("Reactivación Q4")
                .status(Campaign.CampaignStatus.Active).audienceType(Campaign.AudienceType.SEGMENT)
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2026, 12, 31)).build();
        historial = new ArrayList<>();
        interacciones = new ArrayList<>();
        propuestas = new ArrayList<>();
        proyectos = new ArrayList<>();
        ejecutadosPorRunFor = new ArrayList<>();

        when(campaignRepository.findById(20)).thenAnswer(inv -> Optional.of(campana));
        when(audienceService.resolve(any(Campaign.class))).thenReturn(List.of("c1", "c2", "c3", "c4"));
        when(clientRepository.findAllById(any())).thenReturn(List.of(
                cliente("c1", "Ana"), cliente("c2", "Beto"), cliente("c3", "Ceci"), cliente("c4", "Dora")));
        when(executionRepository.findByWorkflowId(WORKFLOW_ID)).thenAnswer(inv -> historial);
        when(interactionRepository.findAll()).thenAnswer(inv -> interacciones);
        when(proposalRepository.findAll()).thenAnswer(inv -> propuestas);
        when(projectRepository.findAll()).thenAnswer(inv -> proyectos);
        when(executionService.runFor(any(Workflow.class), anyString(), any(), any())).thenAnswer(inv -> {
            String clientId = inv.getArgument(1);
            ejecutadosPorRunFor.add(clientId);
            WorkflowExecution e = WorkflowExecution.builder().workflowId(WORKFLOW_ID).clientId(clientId)
                    .executedAt(LocalDateTime.now()).result(WorkflowExecution.ExecutionResult.success).build();
            return new WorkflowExecutionService.RunOutcome(e, DispatchResult.queued("email", "Encolado"));
        });

        service = new WorkflowEligibilityService(campaignRepository, audienceService, clientRepository,
                interactionRepository, proposalRepository, projectRepository, executionRepository,
                60, 3, 15, RELOJ, WorkflowEligibilityService.ZONA_CIMA);
    }

    // ================================================================== campaña

    @Test
    @DisplayName("Campaña pausada: nadie la recibe y el motivo lo dice")
    void campanaNoActiva() {
        campana.setStatus(Campaign.CampaignStatus.Paused);

        WorkflowEligibilityService.Evaluation e = evaluar(workflow(Workflow.TriggerType.no_contact_x_days, null));

        assertThat(e.campaignActive()).isFalse();
        assertThat(e.eligible()).isEmpty();
        assertThat(motivos(e)).containsExactly("La campaña no está activa", "La campaña no está activa",
                "La campaña no está activa", "La campaña no está activa");
    }

    @Test
    @DisplayName("Campaña activa pero fuera de sus fechas")
    void campanaFueraDeFechas() {
        campana.setEndDate(LocalDate.of(2026, 10, 15));

        WorkflowEligibilityService.Evaluation e = evaluar(workflow(Workflow.TriggerType.no_contact_x_days, null));

        assertThat(e.eligible()).isEmpty();
        assertThat(e.skipped().get(0).reason()).isEqualTo("La campaña está fuera de sus fechas (1 oct. al 15 oct.)");
    }

    @Test
    @DisplayName("Automatización inactiva")
    void workflowInactivo() {
        Workflow w = workflow(Workflow.TriggerType.no_contact_x_days, null);
        w.setActive(false);

        assertThat(evaluar(w).skipped().get(0).reason()).isEqualTo("La automatización está inactiva");
    }

    // ================================================================== scheduled_date

    @Test
    @DisplayName("scheduled_date: antes de la fecha nadie lo recibe y se informa la próxima ejecución")
    void fechaProgramadaFutura() {
        Workflow w = workflow(Workflow.TriggerType.scheduled_date, null);
        w.setScheduledAt(LocalDateTime.of(2026, 12, 1, 9, 0));

        WorkflowEligibilityService.Evaluation e = evaluar(w);

        assertThat(e.eligible()).isEmpty();
        assertThat(e.nextRunAt()).isEqualTo(LocalDateTime.of(2026, 12, 1, 9, 0));
        assertThat(e.skipped().get(0).reason()).isEqualTo("Aún no llega la fecha programada (1 dic. 9:00 a. m.)");
    }

    @Test
    @DisplayName("scheduled_date: llegada la fecha, una sola vez por cliente")
    void fechaProgramadaCumplida() {
        Workflow w = workflow(Workflow.TriggerType.scheduled_date, null);
        w.setScheduledAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        historial.add(ejecucion("c1", LocalDateTime.of(2026, 10, 20, 9, 5), WorkflowExecution.ExecutionResult.success));

        WorkflowEligibilityService.Evaluation e = evaluar(w);

        assertThat(ids(e)).containsExactly("c2", "c3", "c4");
        assertThat(e.skipped().get(0).reason()).isEqualTo("Ya lo recibió el 20/10");
    }

    // ================================================================== no_contact_x_days

    @Test
    @DisplayName("no_contact_x_days: más de N días sin contacto (o nunca); se repite pasados N días del último envío")
    void sinContacto() {
        Workflow w = workflow(Workflow.TriggerType.no_contact_x_days, 15);
        interacciones.add(interaccion("c1", LocalDateTime.of(2026, 10, 18, 11, 0), null, "email"));
        interacciones.add(interaccion("c2", LocalDateTime.of(2026, 9, 1, 11, 0), null, "email"));
        // No cuentan como contacto: correo omitido por el cupo y WhatsApp sin enviar.
        interacciones.add(interaccion("c3", LocalDateTime.of(2026, 10, 20, 11, 0), "SKIPPED_LIMIT", "email"));
        interacciones.add(interaccion("c3", LocalDateTime.of(2026, 10, 20, 12, 0), "SIMULATED", "whatsapp"));
        // c2 ya lo recibio hace 21 dias (> 15): puede repetirse. c4 hace 11 dias: todavia no.
        historial.add(ejecucion("c2", LocalDateTime.of(2026, 9, 30, 9, 0), WorkflowExecution.ExecutionResult.success));
        historial.add(ejecucion("c4", LocalDateTime.of(2026, 10, 10, 9, 0), WorkflowExecution.ExecutionResult.success));

        WorkflowEligibilityService.Evaluation e = evaluar(w);

        assertThat(ids(e)).containsExactly("c2", "c3");
        assertThat(motivo(e, "c1")).isEqualTo("Contactado hace 3 días (umbral 15)");
        assertThat(motivo(e, "c4")).isEqualTo("Ya lo recibió el 10/10 (se repite cada 15 días)");
    }

    @Test
    @DisplayName("Reintentos: tope de fallos, espera tras un fallo y cupo diario de correo")
    void reintentos() {
        Workflow w = workflow(Workflow.TriggerType.no_contact_x_days, 15);
        for (int i = 1; i <= 3; i++) {
            historial.add(ejecucion("c2", LocalDateTime.of(2026, 10, 19, 8 + i, 0), WorkflowExecution.ExecutionResult.failed));
        }
        historial.add(ejecucion("c3", LocalDateTime.of(2026, 10, 21, 9, 30), WorkflowExecution.ExecutionResult.failed));
        historial.add(ejecucion("c1", LocalDateTime.of(2026, 10, 21, 8, 0), WorkflowExecution.ExecutionResult.pending));
        historial.add(ejecucion("c4", LocalDateTime.of(2026, 10, 20, 8, 0), WorkflowExecution.ExecutionResult.pending));

        WorkflowEligibilityService.Evaluation e = evaluar(w);

        assertThat(motivo(e, "c2")).isEqualTo("Superó los reintentos (3 intentos fallidos)");
        assertThat(motivo(e, "c3")).isEqualTo("Falló el último intento; se reintenta desde las 10:30 a. m.");
        assertThat(motivo(e, "c1")).isEqualTo("En espera por el cupo diario de correo; se reintenta mañana");
        // El pendiente de ayer se reintenta hoy.
        assertThat(ids(e)).containsExactly("c4");
    }

    // ================================================================== proposal_no_response

    @Test
    @DisplayName("proposal_no_response: propuesta Sent o In_negotiation sin respuesta creada hace N días o más")
    void propuestaSinRespuesta() {
        Workflow w = workflow(Workflow.TriggerType.proposal_no_response, 7);
        propuestas.add(propuesta("c1", Proposal.ProposalStatus.Sent, LocalDate.of(2026, 10, 1)));
        propuestas.add(propuesta("c2", Proposal.ProposalStatus.In_negotiation, LocalDate.of(2026, 10, 18)));
        propuestas.add(propuesta("c4", Proposal.ProposalStatus.Approved, LocalDate.of(2026, 9, 1)));

        WorkflowEligibilityService.Evaluation e = evaluar(w);

        assertThat(ids(e)).containsExactly("c1");
        assertThat(motivo(e, "c2")).isEqualTo("Propuesta enviada hace 3 días (umbral 7)");
        assertThat(motivo(e, "c3")).isEqualTo("Sin propuesta pendiente");
        assertThat(motivo(e, "c4")).isEqualTo("Sin propuesta pendiente");
    }

    // ================================================================== project_completed

    @Test
    @DisplayName("project_completed: clientes con un proyecto completado, una sola vez")
    void proyectoCompletado() {
        Workflow w = workflow(Workflow.TriggerType.project_completed, null);
        proyectos.add(proyecto("c1", "completed"));
        proyectos.add(proyecto("c2", "in_progress"));
        proyectos.add(proyecto("c3", "Completed"));
        historial.add(ejecucion("c3", LocalDateTime.of(2026, 10, 5, 9, 0), WorkflowExecution.ExecutionResult.success));

        WorkflowEligibilityService.Evaluation e = evaluar(w);

        assertThat(ids(e)).containsExactly("c1");
        assertThat(motivo(e, "c2")).isEqualTo("Sin proyecto completado");
        assertThat(motivo(e, "c3")).isEqualTo("Ya lo recibió el 05/10");
    }

    // ================================================================== manual

    @Test
    @DisplayName("manual: el planificador nunca lo corre; ejecutar ahora lo corre para toda la audiencia")
    void manual() {
        Workflow w = workflow(Workflow.TriggerType.manual, null);
        historial.add(ejecucion("c1", LocalDateTime.of(2026, 10, 5, 9, 0), WorkflowExecution.ExecutionResult.success));

        WorkflowEligibilityService.Evaluation planificador = service.evaluate(w, WorkflowEligibilityService.Mode.SCHEDULER);
        WorkflowEligibilityService.Evaluation ahora = service.evaluate(w, WorkflowEligibilityService.Mode.RUN_NOW);

        assertThat(planificador.eligible()).isEmpty();
        assertThat(planificador.skipped().get(0).reason()).contains("Disparador manual");
        assertThat(ids(ahora)).containsExactly("c1", "c2", "c3", "c4");
    }

    // ================================================================== misma logica en los tres caminos

    @Test
    @DisplayName("Vista previa, ejecutar ahora y planificador eligen los mismos clientes en cada disparador")
    void mismaLogicaEnLosTresCaminos() {
        interacciones.add(interaccion("c1", LocalDateTime.of(2026, 10, 18, 11, 0), null, "email"));
        propuestas.add(propuesta("c2", Proposal.ProposalStatus.Sent, LocalDate.of(2026, 10, 1)));
        proyectos.add(proyecto("c3", "completed"));
        historial.add(ejecucion("c4", LocalDateTime.of(2026, 10, 20, 9, 5), WorkflowExecution.ExecutionResult.success));

        for (Workflow.TriggerType disparador : Workflow.TriggerType.values()) {
            Workflow w = workflow(disparador, disparador == Workflow.TriggerType.no_contact_x_days
                    || disparador == Workflow.TriggerType.proposal_no_response ? 7 : null);
            if (disparador == Workflow.TriggerType.scheduled_date) w.setScheduledAt(LocalDateTime.of(2026, 10, 20, 9, 0));
            when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(w));
            when(workflowRepository.findByActiveTrue()).thenReturn(List.of(w));

            WorkflowAutomationService automatizacion = new WorkflowAutomationService(workflowRepository, service, executionService);
            WorkflowSchedulerService planificador = new WorkflowSchedulerService(workflowRepository, service,
                    executionService, new WorkflowExecutionMapper());

            WorkflowPreviewResponse vista = automatizacion.preview(WORKFLOW_ID);
            List<String> deLaVista = vista.eligible().stream().map(WorkflowAudienceClient::clientId).toList();

            ejecutadosPorRunFor.clear();
            WorkflowRunNowResponse ahora = automatizacion.runNow(WORKFLOW_ID, null, "worker-1");
            List<String> deEjecutarAhora = ahora.executed().stream().map(WorkflowExecutedClient::clientId).toList();
            assertThat(ejecutadosPorRunFor).isEqualTo(deEjecutarAhora);

            ejecutadosPorRunFor.clear();
            planificador.evaluateAll();
            List<String> delPlanificador = new ArrayList<>(ejecutadosPorRunFor);

            assertThat(deEjecutarAhora).isEqualTo(deLaVista);
            if (disparador == Workflow.TriggerType.manual) {
                assertThat(delPlanificador).isEmpty();
            } else {
                assertThat(delPlanificador).isEqualTo(deLaVista);
            }
            assertThat(vista.skipped().size() + vista.eligible().size()).isEqualTo(vista.audienceSize());
        }
    }

    @Test
    @DisplayName("Vista previa: datos de la campaña y de la audiencia")
    void datosDeLaVistaPrevia() {
        Workflow w = workflow(Workflow.TriggerType.scheduled_date, null);
        w.setScheduledAt(LocalDateTime.of(2026, 12, 1, 9, 0));
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(w));

        WorkflowPreviewResponse vista = new WorkflowAutomationService(workflowRepository, service, executionService)
                .preview(WORKFLOW_ID);

        assertThat(vista.campaignName()).isEqualTo("Reactivación Q4");
        assertThat(vista.campaignActive()).isTrue();
        assertThat(vista.audienceType()).isEqualTo("SEGMENT");
        assertThat(vista.audienceSize()).isEqualTo(4);
        assertThat(vista.nextRunAt()).isEqualTo(LocalDateTime.of(2026, 12, 1, 9, 0));
        assertThat(vista.skipped().get(0).name()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Ejecutar ahora devuelve resultado y estado de entrega por cliente")
    void resultadoDeEjecutarAhora() {
        Workflow w = workflow(Workflow.TriggerType.manual, null);
        when(workflowRepository.findById(WORKFLOW_ID)).thenReturn(Optional.of(w));

        WorkflowRunNowResponse r = new WorkflowAutomationService(workflowRepository, service, executionService)
                .runNow(WORKFLOW_ID, null, "worker-1");

        assertThat(r.executed()).hasSize(4);
        assertThat(r.executed().get(0).result()).isEqualTo("success");
        assertThat(r.executed().get(0).deliveryStatus()).isEqualTo("QUEUED");
        assertThat(r.executed().get(0).detail()).isEqualTo("Encolado");
        assertThat(r.skipped()).isEmpty();
    }

    // ================================================================== utilidades

    private WorkflowEligibilityService.Evaluation evaluar(Workflow w) {
        return service.evaluate(w, WorkflowEligibilityService.Mode.SCHEDULER);
    }

    private static List<String> ids(WorkflowEligibilityService.Evaluation e) {
        return e.eligible().stream().map(WorkflowEligibilityService.Candidate::clientId).toList();
    }

    private static List<String> motivos(WorkflowEligibilityService.Evaluation e) {
        return e.skipped().stream().map(WorkflowEligibilityService.Skip::reason).toList();
    }

    private static String motivo(WorkflowEligibilityService.Evaluation e, String clientId) {
        return e.skipped().stream().filter(s -> s.clientId().equals(clientId)).findFirst()
                .map(WorkflowEligibilityService.Skip::reason).orElse(null);
    }

    private static Workflow workflow(Workflow.TriggerType disparador, Integer dias) {
        Workflow w = new Workflow();
        w.setWorkflowId(WORKFLOW_ID);
        w.setCampaignId(20);
        w.setWorkflowName("Seguimiento");
        w.setTriggerType(disparador);
        w.setActionType(Workflow.ActionType.send_email);
        w.setNoContactDays(dias);
        w.setActive(true);
        return w;
    }

    private static Client cliente(String id, String nombre) {
        return Client.builder().clientId(id).additionalInfo(nombre).plan("Oro").build();
    }

    private static WorkflowExecution ejecucion(String clientId, LocalDateTime cuando, WorkflowExecution.ExecutionResult r) {
        return WorkflowExecution.builder().workflowId(WORKFLOW_ID).clientId(clientId).executedAt(cuando).result(r).build();
    }

    private static MarketingInteraction interaccion(String clientId, LocalDateTime cuando, String estado, String canal) {
        return MarketingInteraction.builder().clientId(clientId).contactDate(cuando).deliveryStatus(estado)
                .channel(canal).interactionType(MarketingInteraction.InteractionType.message).build();
    }

    private static Proposal propuesta(String clientId, Proposal.ProposalStatus estado, LocalDate creada) {
        return Proposal.builder().clientId(clientId).status(estado).createdDate(creada)
                .responseDate(estado == Proposal.ProposalStatus.Approved ? creada.plusDays(2) : null)
                .estimatedValue(new BigDecimal("1000")).build();
    }

    private static Project proyecto(String clientId, String estado) {
        return Project.builder().projectId("p-" + clientId).clientId(clientId).projectName("Proyecto").status(estado).build();
    }
}
