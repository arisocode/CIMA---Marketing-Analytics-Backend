package com.cimaxis.demo.marketing.service.workflows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.analytics.domain.Project;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.analytics.repository.ProjectRepository;
import com.cimaxis.demo.marketing.domain.campaigns.Campaign;
import com.cimaxis.demo.marketing.domain.campaigns.Proposal;
import com.cimaxis.demo.marketing.domain.interactions.MarketingInteraction;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutionResponse;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowExecutionMapper;
import com.cimaxis.demo.marketing.repository.campaigns.CampaignRepository;
import com.cimaxis.demo.marketing.repository.campaigns.ProposalRepository;
import com.cimaxis.demo.marketing.repository.interactions.MarketingInteractionRepository;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;

/**
 * Planificador de flujos de seguimiento.
 *
 * Recorre periodicamente los workflows activos y decide, segun su trigger_type,
 * que clientes deben recibir la accion configurada. Es el componente que
 * convierte la automatizacion en un proceso autonomo en segundo plano y no en
 * una accion que un operador tiene que lanzar a mano.
 */
@Service
public class WorkflowSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSchedulerService.class);

    private static final Set<String> ESTADOS_PROYECTO_CERRADO =
            Set.of("completed", "closed", "finalizado", "completado", "cerrado");

    private final WorkflowRepository workflowRepository;
    private final CampaignRepository campaignRepository;
    private final ProposalRepository proposalRepository;
    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final MarketingInteractionRepository interactionRepository;
    private final WorkflowExecutionService executionService;
    private final WorkflowExecutionMapper executionMapper;

    @Value("${cimaxis.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${cimaxis.scheduler.default-no-contact-days:15}")
    private int defaultNoContactDays;

    public WorkflowSchedulerService(WorkflowRepository workflowRepository,
                                    CampaignRepository campaignRepository,
                                    ProposalRepository proposalRepository,
                                    ProjectRepository projectRepository,
                                    ClientRepository clientRepository,
                                    MarketingInteractionRepository interactionRepository,
                                    WorkflowExecutionService executionService,
                                    WorkflowExecutionMapper executionMapper) {
        this.workflowRepository = workflowRepository;
        this.campaignRepository = campaignRepository;
        this.proposalRepository = proposalRepository;
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.interactionRepository = interactionRepository;
        this.executionService = executionService;
        this.executionMapper = executionMapper;
    }

    /**
     * Corre cada hora por defecto. Configurable con cimaxis.scheduler.cron.
     */
    @Scheduled(cron = "${cimaxis.scheduler.cron:0 0 * * * *}")
    public void runScheduledWorkflows() {
        if (!schedulerEnabled) {
            return;
        }
        int total = evaluarTodos().size();
        log.info("Planificador de workflows finalizado. Ejecuciones generadas: {}", total);
    }

    /**
     * Evalua todos los workflows activos y devuelve el resultado ya convertido.
     */
    public List<WorkflowExecutionResponse> evaluateAll() {
        return executionMapper.toResponseList(evaluarTodos());
    }

    /** Nucleo de la evaluacion. Trabaja con entidades y no sale de la capa de servicio. */
    private List<WorkflowExecution> evaluarTodos() {
        List<WorkflowExecution> generated = new ArrayList<>();

        for (Workflow workflow : workflowRepository.findByActiveTrue()) {
            if (workflow.getTriggerType() == Workflow.TriggerType.manual) {
                continue;
            }
            try {
                List<String> objetivo = resolveTargetClients(workflow);
                if (objetivo.isEmpty()) {
                    continue;
                }
                generated.addAll(executionService.executeForClients(
                        workflow, objetivo, null, null));
            } catch (Exception e) {
                log.error("Error evaluando el workflow {}: {}",
                        workflow.getWorkflowId(), e.getMessage());
            }
        }
        return generated;
    }

    /**
     * Traduce el trigger de un workflow en la lista de clientes que deben
     * recibir la accion en este momento.
     */
    private List<String> resolveTargetClients(Workflow workflow) {
        return switch (workflow.getTriggerType()) {
            case scheduled_date -> clientesPorFechaProgramada(workflow);
            case no_contact_x_days -> clientesSinContacto(workflow);
            case proposal_no_response -> clientesConPropuestaSinRespuesta(workflow);
            case project_completed -> clientesConProyectoCerrado();
            case manual -> List.of();
        };
    }

    /**
     * La campana define la ventana de vigencia.
     */
    private List<String> clientesPorFechaProgramada(Workflow workflow) {
        Optional<Campaign> campaign = campaignRepository.findById(workflow.getCampaignId());
        if (campaign.isEmpty()) {
            return List.of();
        }
        Campaign c = campaign.get();
        LocalDate hoy = LocalDate.now();

        boolean vigente = c.getStatus() == Campaign.CampaignStatus.Active
                && c.getStartDate() != null
                && !hoy.isBefore(c.getStartDate())
                && (c.getEndDate() == null || !hoy.isAfter(c.getEndDate()));

        return vigente ? List.of(c.getClientId()) : List.of();
    }

    /**
     * Clientes cuyo ultimo contacto registrado supera el umbral de dias
     * configurado en el workflow, incluidos los que nunca han sido contactados.
     */
    private List<String> clientesSinContacto(Workflow workflow) {
        int dias = workflow.getNoContactDays() != null
                ? workflow.getNoContactDays()
                : defaultNoContactDays;
        LocalDateTime limite = LocalDateTime.now().minusDays(dias);

        List<String> objetivo = new ArrayList<>();
        clientRepository.findAll().forEach(client -> {
            MarketingInteraction ultima = interactionRepository
                    .findTopByClientIdOrderByContactDateDesc(client.getClientId());
            if (ultima == null || ultima.getContactDate() == null
                    || ultima.getContactDate().isBefore(limite)) {
                objetivo.add(client.getClientId());
            }
        });
        return objetivo;
    }

    /**
     * Clientes con propuestas enviadas o en negociacion que llevan mas de N
     * dias sin respuesta.
     */
    private List<String> clientesConPropuestaSinRespuesta(Workflow workflow) {
        int dias = workflow.getNoContactDays() != null
                ? workflow.getNoContactDays()
                : defaultNoContactDays;
        LocalDate limite = LocalDate.now().minusDays(dias);

        List<Proposal> pendientes = proposalRepository
                .findByResponseDateIsNullAndStatusInAndCreatedDateLessThanEqual(
                        List.of(Proposal.ProposalStatus.Sent,
                                Proposal.ProposalStatus.In_negotiation),
                        limite);

        Set<String> unicos = new HashSet<>();
        pendientes.forEach(p -> unicos.add(p.getClientId()));
        return new ArrayList<>(unicos);
    }

    /** Clientes con al menos un proyecto en estado cerrado. */
    private List<String> clientesConProyectoCerrado() {
        Set<String> unicos = new HashSet<>();
        for (Project project : projectRepository.findAll()) {
            String estado = project.getStatus() != null
                    ? project.getStatus().toLowerCase().replace("_", " ").trim()
                    : "";
            if (ESTADOS_PROYECTO_CERRADO.contains(estado)) {
                unicos.add(project.getClientId());
            }
        }
        return new ArrayList<>(unicos);
    }
}
