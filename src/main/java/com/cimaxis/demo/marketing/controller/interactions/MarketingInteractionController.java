package com.cimaxis.demo.marketing.controller.interactions;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.interactions.InteractionResponseRequest;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionRequest;
import com.cimaxis.demo.marketing.dto.interactions.MarketingInteractionResponse;
import com.cimaxis.demo.marketing.service.interactions.MarketingInteractionService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Endpoints del historial de interacciones.
 */
@RestController
@RequestMapping("/api/v1/marketing/interactions")
@RequiredArgsConstructor
public class MarketingInteractionController {

    private final MarketingInteractionService interactionService;

    @GetMapping
    public ResponseEntity<List<MarketingInteractionResponse>> getAll(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        if (from != null && to != null) {
            return ResponseEntity.ok(interactionService.findBetween(from, to));
        }
        return ResponseEntity.ok(interactionService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MarketingInteractionResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(interactionService.findById(id));
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<MarketingInteractionResponse>> getByCampaign(
            @PathVariable Integer campaignId) {
        return ResponseEntity.ok(interactionService.findByCampaign(campaignId));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<MarketingInteractionResponse>> getByClient(
            @PathVariable String clientId) {
        return ResponseEntity.ok(interactionService.findByClient(clientId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<MarketingInteractionResponse> register(
            @RequestBody MarketingInteractionRequest request,
            HttpServletRequest servletRequest) {

        String userId = (String) servletRequest.getAttribute("userId");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interactionService.registerManual(request, userId));
    }

    /** Registrar la respuesta del cliente a una interaccion previa. */
    @PatchMapping("/{id}/response")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<MarketingInteractionResponse> registerResponse(
            @PathVariable Integer id,
            @RequestBody InteractionResponseRequest request) {

        return ResponseEntity.ok(interactionService.registerResponse(
                id, request.getResponse(), request.getInteractionType()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        interactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
