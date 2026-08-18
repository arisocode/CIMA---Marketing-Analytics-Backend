package com.cimaxis.demo.marketing.controller.workflows;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.workflows.SchedulerRunResponse;
import com.cimaxis.demo.marketing.dto.workflows.WorkflowExecutionResponse;
import com.cimaxis.demo.marketing.service.workflows.WorkflowSchedulerService;

/**
 * Permite disparar manualmente la evaluacion de los workflows programados.
 */
@RestController
@RequestMapping("/api/v1/marketing/scheduler")
@RequiredArgsConstructor
public class WorkflowSchedulerController {

    private final WorkflowSchedulerService schedulerService;

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<SchedulerRunResponse> runNow() {
        List<WorkflowExecutionResponse> generadas = schedulerService.evaluateAll();

        return ResponseEntity.ok(SchedulerRunResponse.builder()
                .executionsGenerated(generadas.size())
                .executions(generadas)
                .build());
    }
}
