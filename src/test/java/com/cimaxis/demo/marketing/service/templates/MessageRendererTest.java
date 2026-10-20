package com.cimaxis.demo.marketing.service.templates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cimaxis.demo.analytics.domain.Client;

class MessageRendererTest {

    @Test
    void reemplazaLasVariablesYDejaLasDesconocidas() {
        Map<String, String> variables = MessageRenderer.variables("Ana", "Oro", "Muebles Prim");

        String texto = MessageRenderer.render("Hola {nombre}, tu plan {plan} en {empresa}. {otra}", variables);

        assertThat(texto).isEqualTo("Hola Ana, tu plan Oro en Muebles Prim. {otra}");
    }

    @Test
    void valoresPorDefectoSinNombrePlanNiEmpresa() {
        Map<String, String> variables = MessageRenderer.variables("  ", null, null);

        assertThat(variables).containsEntry("nombre", "Cliente")
                .containsEntry("plan", "")
                .containsEntry("empresa", "Cliente");
    }

    @Test
    void plantillaNulaDevuelveNulo() {
        assertThat(MessageRenderer.render(null, Map.of("nombre", "Ana"))).isNull();
    }

    @Test
    void variablesDesdeElClienteSincronizado() {
        Client conNombre = Client.builder().clientId("c-1").additionalInfo("Petal Restore")
                .contactInfo("petal@test.com").plan("Diamante").build();
        Client soloCorreo = Client.builder().clientId("c-2").contactInfo("ana@test.com").build();

        assertThat(MessageRenderer.variables(conNombre)).containsEntry("nombre", "Petal Restore")
                .containsEntry("plan", "Diamante").containsEntry("empresa", "Petal Restore");
        assertThat(MessageRenderer.nombreCliente(soloCorreo)).isEqualTo("ana@test.com");
        assertThat(MessageRenderer.nombreCliente(null)).isEqualTo("Cliente");
    }
}
