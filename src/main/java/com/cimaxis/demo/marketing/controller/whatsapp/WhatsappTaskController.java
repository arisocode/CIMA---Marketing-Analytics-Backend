package com.cimaxis.demo.marketing.controller.whatsapp;

import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.whatsapp.WhatsappPendingItem;
import com.cimaxis.demo.marketing.dto.whatsapp.WhatsappSentResponse;
import com.cimaxis.demo.marketing.service.whatsapp.WhatsappTaskService;

/** Bandeja de WhatsApp por enviar a mano (admin y worker). */
@RestController
@RequestMapping("/api/v1/marketing/whatsapp")
@PreAuthorize("hasAnyRole('admin','worker')")
public class WhatsappTaskController {

    private final WhatsappTaskService whatsappTaskService;

    public WhatsappTaskController(WhatsappTaskService whatsappTaskService) {
        this.whatsappTaskService = whatsappTaskService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<WhatsappPendingItem>> pending() {
        return ResponseEntity.ok(whatsappTaskService.pending());
    }

    @PatchMapping("/pending/{interactionId}/sent")
    public ResponseEntity<WhatsappSentResponse> markSent(@PathVariable Integer interactionId, Principal principal) {
        return ResponseEntity.ok(whatsappTaskService.markSent(interactionId,
                principal != null ? principal.getName() : null));
    }
}
