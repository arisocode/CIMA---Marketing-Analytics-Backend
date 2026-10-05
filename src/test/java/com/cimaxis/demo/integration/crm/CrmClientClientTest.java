package com.cimaxis.demo.integration.crm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmClientClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CrmClientClient client;

    @BeforeEach
    void setUp() {
        client = new CrmClientClient("http://crm-test:28080", restTemplate);
    }

    @Test
    @DisplayName("Retorna lista de clientes cuando el CRM responde con items")
    void retornaListaDeClientesCuandoCrmRespondeConItems() {
        Map<String, Object> data = Map.of("items", List.of(Map.of("id", "c-1", "name", "Acme")));
        Map<String, Object> body = Map.of("data", data);

        when(restTemplate.exchange(
                eq("http://crm-test:28080/api/v1/admin/users?role=client"),
                eq(HttpMethod.GET),
                ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(body));

        List<Map<String, Object>> result = client.getClients("valid-bearer-token");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("id", "c-1");
    }

    @Test
    @DisplayName("Retorna lista vacia si la respuesta no contiene items")
    void retornaListaVaciaSiRespuestaNoContieneItems() {
        when(restTemplate.exchange(
                eq("http://crm-test:28080/api/v1/admin/users?role=client"),
                eq(HttpMethod.GET),
                ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of()));

        List<Map<String, Object>> result = client.getClients("valid-bearer-token");

        assertThat(result).isEmpty();
    }
}
