package com.cimaxis.demo.marketing.service.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envio de correo electronico.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${cimaxis.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${cimaxis.mail.from:no-reply@cima.com.co}")
    private String from;

    @Value("${cimaxis.mail.subject-prefix:[CIMA] }")
    private String subjectPrefix;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public DispatchResult send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return DispatchResult.failed("email", "El cliente no tiene correo registrado en el CRM");
        }

        if (!mailEnabled) {
            log.info("[SIMULACION EMAIL] para={} asunto={} cuerpo={}", to, subject, body);
            return DispatchResult.ok("email", "Simulado (cimaxis.mail.enabled=false) hacia " + to);
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return DispatchResult.failed("email",
                    "No hay JavaMailSender configurado. Revise spring.mail.* en application.properties");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subjectPrefix + subject);
            message.setText(body);
            sender.send(message);
            log.info("Correo enviado a {}", to);
            return DispatchResult.ok("email", "Enviado a " + to);
        } catch (Exception e) {
            log.error("Fallo el envio de correo a {}: {}", to, e.getMessage());
            return DispatchResult.failed("email", e.getMessage());
        }
    }
}
