package com.cimaxis.demo.marketing.controller.templates;

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

import com.cimaxis.demo.marketing.dto.templates.TemplateActiveRequest;
import com.cimaxis.demo.marketing.dto.templates.TemplatePreviewRequest;
import com.cimaxis.demo.marketing.dto.templates.TemplatePreviewResponse;
import com.cimaxis.demo.marketing.dto.templates.TemplateRequest;
import com.cimaxis.demo.marketing.dto.templates.TemplateResponse;
import com.cimaxis.demo.marketing.service.templates.MessageTemplateService;

/**
 * Biblioteca de plantillas de mensajes (admin y worker). Una plantilla no se
 * borra: se desactiva.
 */
@RestController
@RequestMapping("/api/v1/marketing/templates")
public class MessageTemplateController {

    private final MessageTemplateService templateService;

    public MessageTemplateController(MessageTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ResponseEntity<List<TemplateResponse>> list(@RequestParam(required = false) String channel,
                                                       @RequestParam(required = false) String plan) {
        return ResponseEntity.ok(templateService.list(channel, plan));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<TemplateResponse> create(@RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(request));
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<TemplateResponse> update(@PathVariable UUID templateId,
                                                   @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templateService.update(templateId, request));
    }

    @PatchMapping("/{templateId}/active")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<TemplateResponse> setActive(@PathVariable UUID templateId,
                                                      @RequestBody TemplateActiveRequest request) {
        return ResponseEntity.ok(templateService.setActive(templateId, request != null ? request.active() : null));
    }

    @PostMapping("/{templateId}/preview")
    public ResponseEntity<TemplatePreviewResponse> preview(@PathVariable UUID templateId,
                                                           @RequestBody TemplatePreviewRequest request) {
        return ResponseEntity.ok(templateService.preview(templateId, request != null ? request.clientId() : null));
    }
}
