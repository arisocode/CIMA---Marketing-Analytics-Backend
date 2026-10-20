package com.cimaxis.demo.marketing.controller.plans;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.plans.PlanActiveRequest;
import com.cimaxis.demo.marketing.dto.plans.PlanRequest;
import com.cimaxis.demo.marketing.dto.plans.PlanResponse;
import com.cimaxis.demo.marketing.service.plans.MarketingPlanService;

/**
 * Catalogo de planes comerciales. Lectura: admin y worker (regla de
 * SecurityConfig para /api/v1/marketing/**). Escritura: solo admin.
 * Un plan no se borra: se desactiva.
 */
@RestController
@RequestMapping("/api/v1/marketing/plans")
public class MarketingPlanController {

    private final MarketingPlanService planService;

    public MarketingPlanController(MarketingPlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> list(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(planService.list(includeInactive));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<PlanResponse> create(@RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(request));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<PlanResponse> update(@PathVariable UUID planId,
                                               @RequestBody PlanRequest request) {
        return ResponseEntity.ok(planService.update(planId, request));
    }

    @PatchMapping("/{planId}/active")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<PlanResponse> setActive(@PathVariable UUID planId,
                                                  @RequestBody PlanActiveRequest request) {
        return ResponseEntity.ok(planService.setActive(planId, request != null ? request.active() : null));
    }
}
