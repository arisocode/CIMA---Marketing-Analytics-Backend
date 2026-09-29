package com.cimaxis.demo.integration.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cimaxis.demo.integration.crm.CrmAuthClient;
import com.cimaxis.demo.integration.crm.CrmClientClient;
import com.cimaxis.demo.integration.crm.CrmProjectClient;

/**
 * Pruebas de la frontera con el CRM (auth y collab). El CRM es un sistema
 * que CIMAxis no controla, por eso los casos se centran en tolerar
 * respuestas incompletas o con campos en formatos distintos.
 */
@ExtendWith(MockitoExtension.class)
class CrmIntegrationServiceTest {

    private static final String TOKEN = "Bearer token-de-prueba";

    @Mock private CrmAuthClient crmAuthClient;
    @Mock private CrmClientClient crmClientClient;
    @Mock private CrmProjectClient crmProjectClient;

    @InjectMocks private CrmIntegrationService service;

    // ---------------------------------------------------------------- consumo del CRM

    @Test
    @DisplayName("Obtiene los clientes del CRM reenviando el token del usuario")
    void obtieneClientesConElTokenDelUsuario() {
        List<Map<String, Object>> clientes = List.of(Map.of("id", "c-1"));
        when(crmClientClient.getClients(TOKEN)).thenReturn(clientes);

        assertThat(service.getClients(TOKEN)).isEqualTo(clientes);
        verify(crmClientClient).getClients(TOKEN);
    }

    @Test
    @DisplayName("Si el CRM falla al listar clientes, el error se reporta con contexto")
    void reportaErrorConContextoSiFallanLosClientes() {
        when(crmClientClient.getClients(TOKEN)).thenThrow(new IllegalStateException("timeout"));

        assertThatThrownBy(() -> service.getClients(TOKEN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener clientes del CRM")
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("Obtiene los proyectos del modulo de colaboracion con el token del usuario")
    void obtieneProyectosConElTokenDelUsuario() {
        List<Map<String, Object>> proyectos = List.of(Map.of("id", "p-1"));
        when(crmProjectClient.getProjects(TOKEN)).thenReturn(proyectos);

        assertThat(service.getProjects(TOKEN)).isEqualTo(proyectos);
    }

    @Test
    @DisplayName("Si el CRM falla al listar proyectos, el error se reporta con contexto")
    void reportaErrorConContextoSiFallanLosProyectos() {
        when(crmProjectClient.getProjects(TOKEN)).thenThrow(new IllegalStateException("503"));

        assertThatThrownBy(() -> service.getProjects(TOKEN))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener proyectos del CRM");
    }

    // ---------------------------------------------------------------- identificador

    @Test
    @DisplayName("El identificador se toma del campo id cuando existe")
    void identificadorDesdeCampoId() {
        Map<String, Object> cliente = Map.of("id", "uuid-1", "subject", "otro");

        assertThat(service.extractClientId(cliente)).isEqualTo("uuid-1");
    }

    @Test
    @DisplayName("Sin id ni clientId, el identificador se toma del subject de auth")
    void identificadorDesdeSubject() {
        Map<String, Object> cliente = Map.of("subject", "sub-99");

        assertThat(service.extractClientId(cliente)).isEqualTo("sub-99");
    }

    @Test
    @DisplayName("Un identificador numerico se convierte a texto")
    void identificadorNumericoSeConvierteATexto() {
        Map<String, Object> cliente = Map.of("client_id", 42);

        assertThat(service.extractClientId(cliente)).isEqualTo("42");
    }

    @Test
    @DisplayName("Sin ningun campo de identificador devuelve null en lugar de fallar")
    void identificadorAusenteDevuelveNull() {
        assertThat(service.extractClientId(Map.of("email", "a@b.com"))).isNull();
    }

    // ---------------------------------------------------------------- nombre

    @Test
    @DisplayName("Defecto corregido: name nulo no rompe el flujo y se usa nombre y apellido")
    void nombreNuloUsaNombreYApellido() {
        Map<String, Object> cliente = new HashMap<>();
        cliente.put("name", null);
        cliente.put("first_name", "Ana");
        cliente.put("last_name", "Martinez");

        assertThat(service.extractClientName(cliente)).isEqualTo("Ana Martinez");
    }

    @Test
    @DisplayName("Sin nombre de persona se usa la empresa, luego el correo y por ultimo 'Cliente'")
    void nombreCaeAEmpresaYLuegoACorreo() {
        Map<String, Object> juridico = Map.of("companyName", "Moda Bella SAS");
        Map<String, Object> soloCorreo = Map.of("email", "info@modabella.com");

        assertThat(service.extractClientName(juridico)).isEqualTo("Moda Bella SAS");
        assertThat(service.extractClientName(soloCorreo)).isEqualTo("info@modabella.com");
        assertThat(service.extractClientName(Map.of())).isEqualTo("Cliente");
    }

    @Test
    @DisplayName("Un name en blanco no se usa como nombre y el cliente nulo responde 'Cliente'")
    void nombreEnBlancoYClienteNulo() {
        Map<String, Object> cliente = new HashMap<>();
        cliente.put("name", "   ");
        cliente.put("company_name", "Moda Bella SAS");

        assertThat(service.extractClientName(cliente)).isEqualTo("Moda Bella SAS");
        assertThat(service.extractClientName(null)).isEqualTo("Cliente");
    }

    // ---------------------------------------------------------------- correo

    @Test
    @DisplayName("El correo se limpia de espacios y se toma de contactInfo si falta email")
    void correoSeNormalizaYTieneRespaldo() {
        Map<String, Object> conEspacios = Map.of("email", "  ana@cima.dev ");
        Map<String, Object> soloContacto = Map.of("contactInfo", "ventas@cima.dev");

        assertThat(service.extractClientEmail(conEspacios)).isEqualTo("ana@cima.dev");
        assertThat(service.extractClientEmail(soloContacto)).isEqualTo("ventas@cima.dev");
        assertThat(service.extractClientEmail(null)).isNull();
    }
}
