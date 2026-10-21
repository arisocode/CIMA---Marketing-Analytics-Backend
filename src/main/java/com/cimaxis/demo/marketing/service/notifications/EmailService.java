package com.cimaxis.demo.marketing.service.notifications;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cimaxis.demo.marketing.domain.notifications.EmailDelivery;
import com.cimaxis.demo.marketing.dto.notifications.EmailQuotaResponse;
import com.cimaxis.demo.marketing.repository.notifications.EmailDeliveryRepository;
import com.cimaxis.demo.marketing.service.templates.MessageRenderer;
import com.cimaxis.demo.security.jwt.ServiceJwtSigner;
import tools.jackson.databind.json.JsonMapper;

/**
 * Envio de correo transaccional delegando en crm-media (ADR-004).
 *
 * Contrato con crm-media (no cambiar): POST {media}/api/v1/emails/send con un
 * JWT de servicio (iss=sub=crm-marketing, aud=crm-media:email,
 * purpose=email:dispatch, bodyHash, vigencia de 60 s) y el cuerpo
 * {version:1, id, expiresAt, to, content:{subject, html, text}}. crm-media
 * responde 202 cuando encola el correo; por eso el exito se registra como QUEUED.
 *
 * Brevo gratis permite 100 correos al dia, compartidos con crm-auth. El limite
 * cimaxis.mail.daily-limit cuenta los correos QUEUED del dia en America/Bogota
 * y, al alcanzarlo, no se llama a crm-media (SKIPPED_LIMIT).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String AUDIENCE = "crm-media:email";
    private static final String PURPOSE = "email:dispatch";
    private static final Pattern CORREO_VALIDO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Zona horaria de CIMA para el corte diario del limite de correos. */
    public static final ZoneId ZONA_CIMA = ZoneId.of("America/Bogota");

    public static final String ORIGEN_WORKFLOW = "WORKFLOW";
    public static final String ORIGEN_ALERTA = "ADMIN_ALERT";
    public static final String ORIGEN_PRUEBA = "TEST";
    public static final String ORIGEN_PORTAL = "PORTAL";
    public static final String ORIGEN_PROPUESTA = "PROPOSAL";

    private final ServiceJwtSigner jwtSigner;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final EmailDeliveryRepository deliveryRepository;
    private final EmailLayoutRenderer layout;
    private final Clock clock;

    @Value("${cimaxis.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${cimaxis.mail.media-service-url:${MEDIA_SERVICE_URL:http://crm-media:3002}}")
    private String mediaServiceUrl;

    @Value("${cimaxis.mail.subject-prefix:[CIMA]}")
    private String subjectPrefix;

    @Value("${cimaxis.mail.daily-limit:60}")
    private int dailyLimit;

    /**
     * Remitente informativo. El remitente real lo define crm-media (su MAIL_FROM
     * en Brevo): el contrato de crm-media no recibe un campo from.
     */
    @Value("${cimaxis.mail.from:no-reply@cima.com.co}")
    private String from;

    /** Constructor que usa Spring. */
    @Autowired
    public EmailService(ServiceJwtSigner jwtSigner,
                        JsonMapper jsonMapper,
                        EmailDeliveryRepository deliveryRepository,
                        EmailLayoutRenderer layout) {
        this(jwtSigner, jsonMapper, HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build(),
                deliveryRepository, layout, Clock.systemDefaultZone());
    }

    /** Constructor para pruebas: permite simular el cliente HTTP y el reloj. */
    public EmailService(ServiceJwtSigner jwtSigner,
                        JsonMapper jsonMapper,
                        HttpClient httpClient,
                        EmailDeliveryRepository deliveryRepository,
                        EmailLayoutRenderer layout,
                        Clock clock) {
        this.jwtSigner = jwtSigner;
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
        this.deliveryRepository = deliveryRepository;
        this.layout = layout;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Envio
    // ------------------------------------------------------------------

    public DispatchResult send(String to, String subject, String body) {
        return send(to, subject, body, ORIGEN_WORKFLOW);
    }

    /**
     * Envia un correo con la plantilla de marca. body llega con las variables
     * ya reemplazadas. origen: WORKFLOW, ADMIN_ALERT o TEST.
     */
    public DispatchResult send(String to, String subject, String body, String origen) {
        if (to == null || to.isBlank()) {
            return DispatchResult.deliveryFailed("email", "El cliente no tiene correo registrado en el CRM");
        }
        String destinatario = to.trim();
        String asunto = conPrefijo(subject);

        if (!mailEnabled) {
            String detalle = "Simulado (cimaxis.mail.enabled=false) hacia " + destinatario;
            log.info("[SIMULACION EMAIL] para={} asunto={}", destinatario, asunto);
            registrar(destinatario, asunto, DeliveryStatus.SIMULATED, detalle, origen);
            return DispatchResult.simulated("email", detalle);
        }

        long enviadosHoy = sentToday();
        if (enviadosHoy >= dailyLimit) {
            String detalle = "No se envió: se alcanzó el límite diario de correos ("
                    + enviadosHoy + "/" + dailyLimit + "). Se reintentará mañana.";
            log.warn("Limite diario de correos alcanzado ({}/{}); no se envia a {}", enviadosHoy, dailyLimit, destinatario);
            registrar(destinatario, asunto, DeliveryStatus.SKIPPED_LIMIT, detalle, origen);
            return DispatchResult.skippedByLimit("email", detalle);
        }

        try {
            enviarACrmMedia(destinatario, asunto, body);
            String detalle = "Encolado en crm-media para " + destinatario;
            log.info("Correo encolado en crm-media para={}", destinatario);
            registrar(destinatario, asunto, DeliveryStatus.QUEUED, detalle, origen);
            return DispatchResult.queued("email", detalle);
        } catch (Exception e) {
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("Fallo el envio de correo via crm-media a {}: {}", destinatario, error);
            registrar(destinatario, asunto, DeliveryStatus.FAILED, error, origen);
            return DispatchResult.deliveryFailed("email", error);
        }
    }

    /** Correo de prueba con la plantilla de marca y variables de ejemplo. */
    public DispatchResult sendTest(String to) {
        if (to == null || !CORREO_VALIDO.matcher(to.trim()).matches()) {
            throw new IllegalArgumentException("Indique un correo válido en el campo to");
        }
        Map<String, String> ejemplo = MessageRenderer.variables("Equipo CIMA", "Oro", "CIMA");
        String cuerpo = MessageRenderer.render("""
                Hola {nombre}:

                Este es un correo de prueba enviado desde CIMAxis para verificar la configuración de envío de correos.

                Ejemplo de variables: plan {plan}, empresa {empresa}.

                Si recibiste este mensaje, el envío de correos está funcionando.""", ejemplo);
        return send(to.trim(), "Correo de prueba de CIMAxis", cuerpo, ORIGEN_PRUEBA);
    }

    // ------------------------------------------------------------------
    // Cupo diario
    // ------------------------------------------------------------------

    public EmailQuotaResponse quota() {
        long enviados = sentToday();
        return new EmailQuotaResponse(mailEnabled, dailyLimit, enviados, Math.max(0, dailyLimit - enviados));
    }

    /** Correos QUEUED desde la medianoche de hoy en America/Bogota. */
    public long sentToday() {
        return deliveryRepository.countByStatusAndCreatedAtGreaterThanEqual(
                DeliveryStatus.QUEUED.name(), inicioDelDiaEnBogota());
    }

    /**
     * Medianoche de hoy en Bogota expresada en la zona del reloj del servidor,
     * que es la misma con la que se guarda created_at.
     */
    LocalDateTime inicioDelDiaEnBogota() {
        LocalDate hoy = LocalDate.now(clock.withZone(ZONA_CIMA));
        return hoy.atStartOfDay(ZONA_CIMA).withZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    // ------------------------------------------------------------------
    // crm-media
    // ------------------------------------------------------------------

    private void enviarACrmMedia(String to, String asunto, String body) throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = buildPayloadJson(messageId, to, asunto, body);
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
    }

    /** Cuerpo exacto que se envia a crm-media (sin el campo template, reservado para crm-auth). */
    String buildPayloadJson(String messageId, String to, String asunto, String body) throws Exception {
        // crm-media rechaza expiresAt a mas de 7 dias de SU reloj; 3 dias deja margen
        // ante diferencias de hora entre servidores (el correo sale en minutos).
        String expiresAt = Instant.now(clock).plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS).toString();

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("subject", asunto);
        content.put("html", layout.html(asunto, body));
        content.put("text", layout.text(body));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", 1);
        payload.put("id", messageId);
        payload.put("expiresAt", expiresAt);
        payload.put("to", to);
        payload.put("content", content);
        return jsonMapper.writeValueAsString(payload);
    }

    private String conPrefijo(String subject) {
        String asunto = subject == null || subject.isBlank() ? "Mensaje de CIMA" : subject.trim();
        String prefijo = subjectPrefix == null ? "" : subjectPrefix.trim();
        return prefijo.isEmpty() || asunto.startsWith(prefijo) ? asunto : prefijo + " " + asunto;
    }

    private void registrar(String to, String asunto, DeliveryStatus estado, String detalle, String origen) {
        try {
            deliveryRepository.save(EmailDelivery.builder()
                    .deliveryId(UUID.randomUUID())
                    .recipient(to)
                    .subject(asunto != null && asunto.length() > 255 ? asunto.substring(0, 255) : asunto)
                    .status(estado.name())
                    .detail(detalle)
                    .source(origen)
                    .createdAt(LocalDateTime.now(clock))
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo registrar el envio de correo a {}: {}", to, e.getMessage());
        }
    }

    private static String computeSha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
