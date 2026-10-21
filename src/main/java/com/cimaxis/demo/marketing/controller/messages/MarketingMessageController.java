package com.cimaxis.demo.marketing.controller.messages;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.messages.MarkReadResponse;
import com.cimaxis.demo.marketing.dto.messages.MessageResponse;
import com.cimaxis.demo.marketing.dto.messages.MessageThreadResponse;
import com.cimaxis.demo.marketing.dto.messages.StaffMessageRequest;
import com.cimaxis.demo.marketing.dto.messages.StaffMessageResponse;
import com.cimaxis.demo.marketing.service.messages.MarketingMessageService;

/**
 * Bandeja de conversaciones comerciales de CIMA (admin y worker).
 * author_name sale de la cabecera X-User-Name si el gateway la envia; si no, "Equipo CIMA".
 */
@RestController
@RequestMapping("/api/v1/marketing/messages")
@PreAuthorize("hasAnyRole('admin','worker')")
public class MarketingMessageController {

    private final MarketingMessageService messageService;

    public MarketingMessageController(MarketingMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/threads")
    public ResponseEntity<List<MessageThreadResponse>> threads() {
        return ResponseEntity.ok(messageService.threads());
    }

    @GetMapping("/{clientId}")
    public ResponseEntity<List<MessageResponse>> messages(@PathVariable String clientId) {
        return ResponseEntity.ok(messageService.staffMessages(clientId));
    }

    @PostMapping("/{clientId}")
    public ResponseEntity<StaffMessageResponse> send(@PathVariable String clientId,
                                                     @RequestBody(required = false) StaffMessageRequest request,
                                                     @RequestHeader(value = "X-User-Name", required = false) String authorName,
                                                     Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.staffSend(clientId,
                request != null ? request.body() : null,
                request != null ? request.alsoEmail() : null,
                principal != null ? principal.getName() : null,
                authorName));
    }

    @PatchMapping("/{clientId}/read")
    public ResponseEntity<MarkReadResponse> markRead(@PathVariable String clientId) {
        return ResponseEntity.ok(messageService.markRead(clientId));
    }
}
