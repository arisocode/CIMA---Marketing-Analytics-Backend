package com.cimaxis.demo.marketing.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.cimaxis.demo.marketing.domain.notifications.EmailDelivery;
import com.cimaxis.demo.marketing.dto.notifications.EmailQuotaResponse;
import com.cimaxis.demo.marketing.repository.notifications.EmailDeliveryRepository;
import com.cimaxis.demo.security.jwt.ServiceJwtSigner;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    /** 2026-10-19 03:00 UTC = 2026-10-18 22:00 en Bogota (UTC-5). */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneOffset.UTC);

    @Mock private ServiceJwtSigner jwtSigner;
    @Mock private HttpClient httpClient;
    @Mock private EmailDeliveryRepository deliveryRepository;

    private EmailService emailService;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        EmailLayoutRenderer layout = new EmailLayoutRenderer("", "CIMA", "hola@cima.test", "", "");
        emailService = new EmailService(jwtSigner, jsonMapper, httpClient, deliveryRepository, layout, RELOJ);
        ReflectionTestUtils.setField(emailService, "mediaServiceUrl", "http://crm-media:3002");
        ReflectionTestUtils.setField(emailService, "subjectPrefix", "[CIMA]");
        ReflectionTestUtils.setField(emailService, "dailyLimit", 60);
        when(jwtSigner.signToken(any(), any(), any())).thenReturn("mocked.service.jwt");
    }

    @Test
    void rejectsEmptyOrNullRecipient() {
        DispatchResult nullResult = emailService.send(null, "Asunto", "Cuerpo");
        assertThat(nullResult.delivered()).isFalse();
        assertThat(nullResult.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(nullResult.detail()).contains("no tiene correo registrado");

        DispatchResult blankResult = emailService.send("   ", "Asunto", "Cuerpo");
        assertThat(blankResult.delivered()).isFalse();
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void simulatesAndLogsEmailWhenMailIsDisabled() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        DispatchResult result = emailService.send("cliente@example.com", "Bienvenido", "Hola mundo");

        assertThat(result.delivered()).isTrue();
        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.SIMULATED);
        assertThat(result.detail()).contains("Simulado");
        assertThat(registrado().getStatus()).isEqualTo("SIMULATED");
        verify(jwtSigner, never()).signToken(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void queuesEmailViaMediaServiceWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        when(deliveryRepository.countByStatusAndCreatedAtGreaterThanEqual(eq("QUEUED"), any())).thenReturn(10L);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(202);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        DispatchResult result = emailService.send("cliente@example.com", "Factura", "Detalle de factura");

        assertThat(result.delivered()).isTrue();
        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.QUEUED);
        verify(jwtSigner).signToken(eq("crm-media:email"), eq("email:dispatch"), anyString());
        EmailDelivery registro = registrado();
        assertThat(registro.getStatus()).isEqualTo("QUEUED");
        assertThat(registro.getRecipient()).isEqualTo("cliente@example.com");
        assertThat(registro.getSubject()).isEqualTo("[CIMA] Factura");
        assertThat(registro.getSource()).isEqualTo(EmailService.ORIGEN_WORKFLOW);
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsMediaServiceWhenDailyLimitIsReached() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        when(deliveryRepository.countByStatusAndCreatedAtGreaterThanEqual(eq("QUEUED"), any())).thenReturn(60L);

        DispatchResult result = emailService.send("cliente@example.com", "Factura", "Detalle");

        assertThat(result.delivered()).isFalse();
        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.SKIPPED_LIMIT);
        assertThat(result.detail()).contains("límite diario").contains("60/60");
        assertThat(registrado().getStatus()).isEqualTo("SKIPPED_LIMIT");
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(jwtSigner, never()).signToken(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesHttpErrorFromMediaService() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"error\":\"INVALID_EMAIL\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        DispatchResult result = emailService.send("invalido@test.com", "Test", "Texto");

        assertThat(result.delivered()).isFalse();
        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.detail()).contains("HTTP 400");
        assertThat(registrado().getStatus()).isEqualTo("FAILED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesNetworkFailureGracefully() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        DispatchResult result = emailService.send("cliente@test.com", "Test", "Texto");

        assertThat(result.delivered()).isFalse();
        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.detail()).contains("Connection refused");
    }

    @Test
    void payloadKeepsTheMediaContractAndUsesTheBrandTemplate() throws Exception {
        String json = emailService.buildPayloadJson("id-1", "cliente@test.com", "[CIMA] Hola", "Hola <b>Ana</b>");
        JsonNode payload = jsonMapper.readTree(json);

        assertThat(payload.path("version").asInt()).isEqualTo(1);
        assertThat(payload.path("id").asText()).isEqualTo("id-1");
        assertThat(payload.path("to").asText()).isEqualTo("cliente@test.com");
        assertThat(payload.has("template")).isFalse();
        assertThat(payload.path("content").path("subject").asText()).isEqualTo("[CIMA] Hola");
        assertThat(payload.path("content").path("html").asText())
                .contains(EmailLayoutRenderer.ROJO_CIMA)
                .contains("Hola &lt;b&gt;Ana&lt;/b&gt;")
                .contains("hola@cima.test");
        assertThat(payload.path("content").path("text").asText()).startsWith("Hola <b>Ana</b>");
        // Vence dentro de los 7 dias que acepta crm-media.
        assertThat(Instant.parse(payload.path("expiresAt").asText()))
                .isBefore(Instant.parse("2026-10-26T03:00:00Z"));
    }

    @Test
    void quotaCountsQueuedEmailsSinceMidnightInBogota() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        when(deliveryRepository.countByStatusAndCreatedAtGreaterThanEqual(eq("QUEUED"), any())).thenReturn(45L);

        EmailQuotaResponse cupo = emailService.quota();

        ArgumentCaptor<LocalDateTime> desde = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deliveryRepository).countByStatusAndCreatedAtGreaterThanEqual(eq("QUEUED"), desde.capture());
        // Medianoche del 18-10 en Bogota = 05:00 UTC (zona del reloj del servidor en esta prueba).
        assertThat(desde.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 18, 5, 0));
        assertThat(cupo.enabled()).isTrue();
        assertThat(cupo.dailyLimit()).isEqualTo(60);
        assertThat(cupo.sentToday()).isEqualTo(45L);
        assertThat(cupo.remaining()).isEqualTo(15L);
    }

    @Test
    void quotaRemainingNeverGoesNegative() {
        when(deliveryRepository.countByStatusAndCreatedAtGreaterThanEqual(eq("QUEUED"), any())).thenReturn(70L);

        assertThat(emailService.quota().remaining()).isZero();
    }

    @Test
    void testEmailUsesTemplateVariablesAndRejectsInvalidAddresses() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        DispatchResult result = emailService.sendTest("admin@cima.test");

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.SIMULATED);
        EmailDelivery registro = registrado();
        assertThat(registro.getSource()).isEqualTo(EmailService.ORIGEN_PRUEBA);
        assertThat(registro.getSubject()).isEqualTo("[CIMA] Correo de prueba de CIMAxis");
        assertThatThrownBy(() -> emailService.sendTest("no-es-correo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correo válido");
    }

    private EmailDelivery registrado() {
        ArgumentCaptor<EmailDelivery> registro = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(deliveryRepository).save(registro.capture());
        return registro.getValue();
    }
}
