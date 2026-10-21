package com.cimaxis.demo.marketing.controller.portal;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.messages.ClientMessageRequest;
import com.cimaxis.demo.marketing.dto.messages.MessageResponse;
import com.cimaxis.demo.marketing.dto.portal.ClientProposalResponse;
import com.cimaxis.demo.marketing.dto.portal.ClientSummaryResponse;
import com.cimaxis.demo.marketing.dto.portal.ProposalDecisionRequest;
import com.cimaxis.demo.marketing.service.messages.MarketingMessageService;
import com.cimaxis.demo.marketing.service.portal.ClientPortalService;

/**
 * Portal del rol client. El clientId sale siempre de la autenticacion
 * (Principal = X-User-Sub), nunca de la URL ni del cuerpo.
 */
@RestController
@RequestMapping("/api/v1/marketing/me")
@PreAuthorize("hasRole('client')")
public class ClientPortalController {

    private final ClientPortalService portalService;
    private final MarketingMessageService messageService;

    public ClientPortalController(ClientPortalService portalService, MarketingMessageService messageService) {
        this.portalService = portalService;
        this.messageService = messageService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ClientSummaryResponse> summary(Principal principal) {
        return ResponseEntity.ok(portalService.summary(sub(principal)));
    }

    @GetMapping("/proposals")
    public ResponseEntity<List<ClientProposalResponse>> proposals(Principal principal) {
        return ResponseEntity.ok(portalService.proposals(sub(principal)));
    }

    @PatchMapping("/proposals/{id}/decision")
    public ResponseEntity<ClientProposalResponse> decide(Principal principal, @PathVariable Integer id,
                                                         @RequestBody(required = false) ProposalDecisionRequest request) {
        return ResponseEntity.ok(portalService.decide(sub(principal), id, request));
    }

    @GetMapping("/messages")
    public ResponseEntity<List<MessageResponse>> messages(Principal principal) {
        return ResponseEntity.ok(messageService.clientMessages(sub(principal)));
    }

    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> send(Principal principal,
                                                @RequestBody(required = false) ClientMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.clientSend(sub(principal), request != null ? request.body() : null));
    }

    private static String sub(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
