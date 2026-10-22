package com.cimaxis.demo.marketing.service.workflows;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowAudienceClient;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutedClient;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowPreviewResponse;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowRunNowResponse;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowSkippedClient;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;

/**
 * Ver y ejecutar una automatizacion puntual. Ambas operaciones usan
 * WorkflowEligibilityService, igual que el planificador.
 */
@Service
public class WorkflowAutomationService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAutomationService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowEligibilityService eligibilityService;
    private final WorkflowExecutionService executionService;

    public WorkflowAutomationService(WorkflowRepository workflowRepository,
                                     WorkflowEligibilityService eligibilityService,
                                     WorkflowExecutionService executionService) {
        this.workflowRepository = workflowRepository;
        this.eligibilityService = eligibilityService;
        this.executionService = executionService;
    }

    /** Que pasaria si el planificador corriera ahora (para manual: que haria "ejecutar ahora"). */
    public WorkflowPreviewResponse preview(Integer workflowId) {
        Workflow workflow = require(workflowId);
        WorkflowEligibilityService.Mode modo = workflow.getTriggerType() == Workflow.TriggerType.manual
                ? WorkflowEligibilityService.Mode.RUN_NOW : WorkflowEligibilityService.Mode.SCHEDULER;
        WorkflowEligibilityService.Evaluation e = eligibilityService.evaluate(workflow, modo);
        return new WorkflowPreviewResponse(
                e.campaign() != null ? e.campaign().getCampaignName() : null,
                e.campaignActive(),
                e.audienceType() != null ? e.audienceType().name() : null,
                e.audienceSize(),
                e.nextRunAt(),
                e.eligible().stream().map(c -> new WorkflowAudienceClient(c.clientId(), c.name(), c.plan())).toList(),
                omitidos(e));
    }

    /** Evalua solo este workflow con las reglas del planificador y ejecuta la accion para los elegibles. */
    public WorkflowRunNowResponse runNow(Integer workflowId, String bearerToken, String userId) {
        Workflow workflow = require(workflowId);
        WorkflowEligibilityService.Evaluation e =
                eligibilityService.evaluate(workflow, WorkflowEligibilityService.Mode.RUN_NOW);
        List<WorkflowExecutedClient> ejecutados = new ArrayList<>();
        for (WorkflowEligibilityService.Candidate c : e.eligible()) {
            try {
                WorkflowExecutionService.RunOutcome r = executionService.runFor(workflow, c.clientId(), bearerToken, userId);
                WorkflowExecution ejecucion = r.execution();
                ejecutados.add(new WorkflowExecutedClient(c.clientId(), c.name(),
                        ejecucion.getResult() != null ? ejecucion.getResult().name() : null,
                        r.dispatch() != null && r.dispatch().deliveryStatus() != null
                                ? r.dispatch().deliveryStatus().name() : null,
                        r.dispatch() != null ? r.dispatch().detail() : null));
            } catch (Exception ex) {
                log.error("Fallo la ejecucion del workflow {} para el cliente {}: {}", workflowId, c.clientId(), ex.getMessage());
                ejecutados.add(new WorkflowExecutedClient(c.clientId(), c.name(),
                        WorkflowExecution.ExecutionResult.failed.name(), null,
                        "Error al ejecutar: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName())));
            }
        }
        return new WorkflowRunNowResponse(ejecutados, omitidos(e));
    }

    private static List<WorkflowSkippedClient> omitidos(WorkflowEligibilityService.Evaluation e) {
        return e.skipped().stream().map(s -> new WorkflowSkippedClient(s.clientId(), s.name(), s.reason())).toList();
    }

    private Workflow require(Integer workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> ResourceNotFoundException.de("Workflow", workflowId));
    }
}
