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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmProjectClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CrmProjectClient client;

    @BeforeEach
    void setUp() {
        client = new CrmProjectClient("http://crm-test:28080", restTemplate);
    }

    @Test
    @DisplayName("Recupera proyectos paginados acumulando resultados")
    void recuperaProyectosPaginadosAcumulandoResultados() {
        Map<String, Object> page1 = Map.of("data", Map.of(
                "items", List.of(Map.of("id", "p-1", "title", "Project 1")),
                "total_pages", 2
        ));
        Map<String, Object> page2 = Map.of("data", Map.of(
                "items", List.of(Map.of("id", "p-2", "title", "Project 2")),
                "total_pages", 2
        ));

        when(restTemplate.exchange(
                contains("page=1"),
                eq(HttpMethod.GET),
                ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(page1));

        when(restTemplate.exchange(
                contains("page=2"),
                eq(HttpMethod.GET),
                ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(page2));

        List<Map<String, Object>> projects = client.getProjects("test-token");

        assertThat(projects).hasSize(2);
    }

    @Test
    @DisplayName("Lanza excepcion si el payload no cumple el contrato de objeto paginado")
    void lanzaExcepcionSiPayloadInvalido() {
        when(restTemplate.exchange(
                contains("page=1"),
                eq(HttpMethod.GET),
                ArgumentMatchers.<HttpEntity<Void>>any(),
                eq(Map.class)
        )).thenReturn(ResponseEntity.ok(Map.of("data", "invalid-string")));

        assertThatThrownBy(() -> client.getProjects("test-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Contrato inválido de Collab");
    }
}
