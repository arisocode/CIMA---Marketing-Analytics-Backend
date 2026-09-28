package com.cimaxis.demo.marketing.service.notifications;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.security.jwt.ServiceJwtSigner;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envio de correo electronico transaccional desacoplado delegando en crm-media (ADR-004).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String AUDIENCE = "crm-media:email";
    private static final String PURPOSE = "email:dispatch";

    private final ServiceJwtSigner jwtSigner;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    @Value("${cimaxis.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${cimaxis.mail.media-service-url:${MEDIA_SERVICE_URL:http://crm-media:3002}}")
    private String mediaServiceUrl;

    @Value("${cimaxis.mail.subject-prefix:[CIMA] }")
    private String subjectPrefix;

    public EmailService(ServiceJwtSigner jwtSigner, JsonMapper jsonMapper) {
        this(jwtSigner, jsonMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    public EmailService(ServiceJwtSigner jwtSigner, JsonMapper jsonMapper, HttpClient httpClient) {
        this.jwtSigner = jwtSigner;
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
    }

    public DispatchResult send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return DispatchResult.failed("email", "El cliente no tiene correo registrado en el CRM");
        }

        if (!mailEnabled) {
            log.info("[SIMULACION EMAIL] para={} asunto={} cuerpo={}", to, subject, body);
            return DispatchResult.ok("email", "Simulado (cimaxis.mail.enabled=false) hacia " + to);
        }

        try {
            return dispatchToMedia(to, subjectPrefix + subject, body);
        } catch (Exception e) {
            log.error("Fallo el envio de correo via crm-media a {}: {}", to, e.getMessage());
            return DispatchResult.failed("email", e.getMessage());
        }
    }

    private DispatchResult dispatchToMedia(String to, String fullSubject, String body) throws Exception {
        String messageId = UUID.randomUUID().toString();
        String expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS).toString();

        Map<String, Object> payload = Map.of(
            "version", 1,
            "id", messageId,
            "expiresAt", expiresAt,
            "to", to,
            "content", Map.of(
                "subject", fullSubject,
                "html", "<p>" + escapeHtml(body) + "</p>",
                "text", body
            )
        );

        String rawJson = jsonMapper.writeValueAsString(payload);
        String bodyHash = computeSha256Hex(rawJson);
        String serviceJwt = jwtSigner.signToken(AUDIENCE, PURPOSE, bodyHash);

        URI targetUri = URI.create(mediaServiceUrl.replaceAll("/+$", "") + "/api/v1/emails/send");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + serviceJwt)
                .header("Idempotency-Key", messageId)
                .POST(HttpRequest.BodyPublishers.ofString(rawJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 202) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }

        log.info("Correo encolado exitosamente en crm-media para={}", to);
        return DispatchResult.ok("email", "Enviado a " + to);
    }

    private static String computeSha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
