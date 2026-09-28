package com.cimaxis.demo.marketing.service.segmentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;

/**
 * Asignacion del plan comercial de cada cliente, individual y masiva.
 * El plan es el criterio principal de segmentacion de campanas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientPlanServiceTest {

    @Mock private ClientRepository clientRepository;

    @InjectMocks private ClientPlanService service;

    @Test
    @DisplayName("Consultar un cliente que no existe responde recurso no encontrado")
    void clienteInexistente() {
        when(clientRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cliente no encontrado: x");
    }

    @Test
    @DisplayName("El plan se asigna sin importar mayusculas ni espacios y registra la fecha")
    void asignaPlanNormalizado() {
        Client cliente = cliente("c-1", null);
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client actualizado = service.assignPlan("c-1", "  oro ");

        assertThat(actualizado.getPlan()).isEqualTo(Client.Plan.Oro);
        assertThat(actualizado.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Enviar un plan vacio le quita el plan al cliente")
    void planVacioQuitaElPlan() {
        Client cliente = cliente("c-1", Client.Plan.Oro);
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.assignPlan("c-1", "").getPlan()).isNull();
    }

    @Test
    @DisplayName("Un plan que no existe se rechaza y no se guarda nada")
    void planInvalidoSeRechaza() {
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente("c-1", null)));

        assertThatThrownBy(() -> service.assignPlan("c-1", "Bronce"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan invalido: Bronce");
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Los planes vigentes son Platinum, Oro y Diamante; los antiguos se rechazan")
    void catalogoDePlanesVigente() {
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente("c-1", null)));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.assignPlan("c-1", "platinum").getPlan()).isEqualTo(Client.Plan.Platinum);
        assertThat(service.assignPlan("c-1", "Diamante").getPlan()).isEqualTo(Client.Plan.Diamante);
        assertThatThrownBy(() -> service.assignPlan("c-1", "Esmeralda"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[Platinum, Oro, Diamante]");
    }

    @Test
    @DisplayName("Consultar por plan traduce el texto al plan del catalogo")
    void consultaPorPlan() {
        List<Client> deOro = List.of(cliente("c-1", Client.Plan.Oro));
        when(clientRepository.findByPlan(Client.Plan.Oro)).thenReturn(deOro);

        assertThat(service.findByPlan("ORO")).isEqualTo(deOro);
    }

    @Test
    @DisplayName("Consultar por un plan vacio se rechaza")
    void consultaPorPlanVacioSeRechaza() {
        assertThatThrownBy(() -> service.findByPlan(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El plan es obligatorio");
    }

    @Test
    @DisplayName("Lista solo los clientes sincronizados que aun no tienen plan")
    void listaClientesSinPlan() {
        when(clientRepository.findAll()).thenReturn(List.of(
                cliente("c-1", Client.Plan.Oro), cliente("c-2", null), cliente("c-3", null)));

        assertThat(service.findSinPlan()).extracting(Client::getClientId).containsExactly("c-2", "c-3");
    }

    @Test
    @DisplayName("La asignacion masiva aplica las validas y reporta cada error sin detenerse")
    void asignacionMasivaReportaErroresPorCliente() {
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente("c-1", null)));
        when(clientRepository.findById("c-2")).thenReturn(Optional.of(cliente("c-2", null)));
        when(clientRepository.findById("c-3")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, String> asignaciones = new LinkedHashMap<>();
        asignaciones.put("c-1", "Oro");
        asignaciones.put("c-2", "Bronce");
        asignaciones.put("c-3", "Oro");

        Map<String, Object> resultado = service.assignPlanBulk(asignaciones);

        assertThat(resultado.get("actualizados")).isEqualTo(1);
        assertThat(resultado.get("clientes")).isEqualTo(List.of("c-1"));
        @SuppressWarnings("unchecked")
        Map<String, String> errores = (Map<String, String>) resultado.get("errores");
        assertThat(errores).containsOnlyKeys("c-2", "c-3");
    }

    private static Client cliente(String id, Client.Plan plan) {
        return Client.builder().clientId(id).userId(id).plan(plan).build();
    }
}
