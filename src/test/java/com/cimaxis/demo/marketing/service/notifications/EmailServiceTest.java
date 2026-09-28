package com.cimaxis.demo.marketing.service.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.cimaxis.demo.security.jwt.ServiceJwtSigner;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private ServiceJwtSigner jwtSigner;

    @Mock
    private HttpClient httpClient;

    private EmailService emailService;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        emailService = new EmailService(jwtSigner, jsonMapper, httpClient);
    }

    @Test
    void rejectsEmptyOrNullRecipient() {
        DispatchResult nullResult = emailService.send(null, "Asunto", "Cuerpo");
        assertThat(nullResult.delivered()).isFalse();
        assertThat(nullResult.detail()).contains("no tiene correo registrado");

        DispatchResult blankResult = emailService.send("   ", "Asunto", "Cuerpo");
        assertThat(blankResult.delivered()).isFalse();
        assertThat(blankResult.detail()).contains("no tiene correo registrado");
    }

    @Test
    void simulatesEmailWhenMailIsDisabled() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        DispatchResult result = emailService.send("cliente@example.com", "Bienvenido", "Hola mundo");

        assertThat(result.delivered()).isTrue();
        assertThat(result.detail()).contains("Simulado");
        verify(jwtSigner, never()).signToken(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesEmailViaMediaServiceWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "mediaServiceUrl", "http://crm-media:3002");
        ReflectionTestUtils.setField(emailService, "subjectPrefix", "[CIMA] ");

        when(jwtSigner.signToken(any(), any(), any())).thenReturn("mocked.service.jwt");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(202);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DispatchResult result = emailService.send("cliente@example.com", "Factura", "Detalle de factura");

        assertThat(result.delivered()).isTrue();
        assertThat(result.detail()).isEqualTo("Enviado a cliente@example.com");
        verify(jwtSigner).signToken(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesHttpErrorFromMediaService() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "mediaServiceUrl", "http://crm-media:3002");

        when(jwtSigner.signToken(any(), any(), any())).thenReturn("mocked.service.jwt");

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"error\":\"INVALID_EMAIL\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        DispatchResult result = emailService.send("invalido@test.com", "Test", "Texto");

        assertThat(result.delivered()).isFalse();
        assertThat(result.detail()).contains("HTTP 400");
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesNetworkFailureGracefully() throws Exception {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "mediaServiceUrl", "http://crm-media:3002");

        when(jwtSigner.signToken(any(), any(), any())).thenReturn("mocked.service.jwt");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        DispatchResult result = emailService.send("cliente@test.com", "Test", "Texto");

        assertThat(result.delivered()).isFalse();
        assertThat(result.detail()).contains("Connection refused");
    }
}
