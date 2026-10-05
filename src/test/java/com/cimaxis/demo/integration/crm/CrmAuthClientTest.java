package com.cimaxis.demo.integration.crm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmAuthClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CrmAuthClient client;

    @BeforeEach
    void setUp() {
        client = new CrmAuthClient("http://crm-test:28080", restTemplate);
    }

    @Test
    @DisplayName("Retorna access_token cuando el CRM responde exitosamente")
    void retornaAccessTokenCuandoCrmRespondeExitosamente() {
        Map<String, Object> body = Map.of("data", Map.of("access_token", "jwt-token-123"));
        when(restTemplate.postForEntity(
                eq("http://crm-test:28080/api/v1/auth/login"),
                ArgumentMatchers.<HttpEntity<Map<String, String>>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(body));

        String token = client.login("admin@cima.com.co", "secret");

        assertThat(token).isEqualTo("jwt-token-123");
    }

    @Test
    @DisplayName("Lanza excepcion si la respuesta no contiene access_token")
    void lanzaExcepcionSiRespuestaNoContieneToken() {
        when(restTemplate.postForEntity(
                eq("http://crm-test:28080/api/v1/auth/login"),
                ArgumentMatchers.<HttpEntity<Map<String, String>>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of()));

        assertThatThrownBy(() -> client.login("admin@cima.com.co", "secret"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo obtener el token del CRM");
    }
}
