package com.cimaxis.demo.marketing.controller.notifications;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.notifications.EmailQuotaResponse;
import com.cimaxis.demo.marketing.dto.notifications.TestEmailRequest;
import com.cimaxis.demo.marketing.dto.notifications.TestEmailResponse;
import com.cimaxis.demo.marketing.service.notifications.DeliveryStatus;
import com.cimaxis.demo.marketing.service.notifications.DispatchResult;
import com.cimaxis.demo.marketing.service.notifications.EmailService;

/**
 * Estado del envio de correos: cupo diario (admin y worker) y correo de prueba
 * (solo admin).
 */
@RestController
@RequestMapping("/api/v1/marketing/notifications")
public class NotificationController {

    private final EmailService emailService;

    public NotificationController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/email-quota")
    public ResponseEntity<EmailQuotaResponse> emailQuota() {
        return ResponseEntity.ok(emailService.quota());
    }

    @PostMapping("/test-email")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<TestEmailResponse> testEmail(@RequestBody TestEmailRequest request) {
        DispatchResult resultado = emailService.sendTest(request != null ? request.to() : null);
        DeliveryStatus estado = resultado.deliveryStatus() != null ? resultado.deliveryStatus() : DeliveryStatus.FAILED;
        return ResponseEntity.ok(new TestEmailResponse(estado.name(), resultado.detail()));
    }
}
