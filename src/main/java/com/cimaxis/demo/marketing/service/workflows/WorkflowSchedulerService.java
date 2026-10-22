package com.cimaxis.demo.marketing.service.workflows;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.marketing.domain.workflows.Workflow;
import com.cimaxis.demo.marketing.domain.workflows.WorkflowExecution;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutionResponse;
import com.cimaxis.demo.marketing.mapper.workflows.WorkflowExecutionMapper;
import com.cimaxis.demo.marketing.repository.workflows.WorkflowRepository;

/**
 * Planificador de automatizaciones. Las reglas de a quien le toca cada workflow
 * estan en WorkflowEligibilityService (las mismas de la vista previa y de
 * "ejecutar ahora").
 */
@Service
public class WorkflowSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSchedulerService.class);

    private final WorkflowRepository workflowRepository;
    private final WorkflowEligibilityService eligibilityService;
    private final WorkflowExecutionService executionService;
    private final WorkflowExecutionMapper executionMapper;

    @Value("${cimaxis.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public WorkflowSchedulerService(WorkflowRepository workflowRepository,
                                    WorkflowEligibilityService eligibilityService,
                                    WorkflowExecutionService executionService,
                                    WorkflowExecutionMapper executionMapper) {
        this.workflowRepository = workflowRepository;
        this.eligibilityService = eligibilityService;
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

    /**
     * Para cada workflow activo (menos los manual), WorkflowEligibilityService decide
     * que clientes de la audiencia de su campaña lo reciben hoy; aqui solo se ejecuta.
     */
    private List<WorkflowExecution> evaluarTodos() {
        List<WorkflowExecution> generated = new ArrayList<>();
        for (Workflow workflow : workflowRepository.findByActiveTrue()) {
            if (workflow.getTriggerType() == Workflow.TriggerType.manual) {
                continue;
            }
            try {
                WorkflowEligibilityService.Evaluation evaluacion =
                        eligibilityService.evaluate(workflow, WorkflowEligibilityService.Mode.SCHEDULER);
                for (WorkflowEligibilityService.Candidate candidato : evaluacion.eligible()) {
                    generated.add(executionService.runFor(workflow, candidato.clientId(), null, null).execution());
                }
            } catch (Exception e) {
                log.error("Error evaluando el workflow {}: {}", workflow.getWorkflowId(), e.getMessage());
            }
        }
        return generated;
    }
}
