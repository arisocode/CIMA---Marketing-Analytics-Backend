package com.cimaxis.demo.marketing.service.segmentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.domain.Client;
import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;
import com.cimaxis.demo.marketing.service.plans.MarketingPlanService;

/**
 * Asignacion del plan comercial de cada cliente, individual y masiva. Los
 * planes validos salen del catalogo marketing_plans (aqui: Platinum, Oro y
 * Diamante activos y Esmeralda inactivo).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientPlanServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private MarketingPlanRepository planRepository;

    private ClientPlanService service;

    @BeforeEach
    void setUp() {
        List<MarketingPlan> catalogo = List.of(
                plan("Platinum", true, 1), plan("Oro", true, 2),
                plan("Diamante", true, 3), plan("Esmeralda", false, 4));
        when(planRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(catalogo);
        when(planRepository.findByNameIgnoreCase(any())).thenAnswer(inv -> catalogo.stream()
                .filter(p -> p.getName().equalsIgnoreCase(inv.getArgument(0)))
                .findFirst());
        service = new ClientPlanService(clientRepository, new MarketingPlanService(planRepository, clientRepository));
    }

    @Test
    @DisplayName("Consultar un cliente que no existe responde recurso no encontrado")
    void clienteInexistente() {
        when(clientRepository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cliente no encontrado: x");
    }

    @Test
    @DisplayName("El plan se asigna con el nombre del catalogo, sin importar mayusculas ni espacios")
    void asignaPlanNormalizado() {
        Client cliente = cliente("c-1", null);
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client actualizado = service.assignPlan("c-1", "  oro ");

        assertThat(actualizado.getPlan()).isEqualTo("Oro");
        assertThat(actualizado.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Enviar un plan vacio le quita el plan al cliente")
    void planVacioQuitaElPlan() {
        Client cliente = cliente("c-1", "Oro");
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.assignPlan("c-1", "").getPlan()).isNull();
    }

    @Test
    @DisplayName("Un plan que no existe se rechaza, lista los planes activos y no se guarda nada")
    void planInvalidoSeRechaza() {
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente("c-1", null)));

        assertThatThrownBy(() -> service.assignPlan("c-1", "Bronce"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plan inválido: Bronce")
                .hasMessageContaining("[Platinum, Oro, Diamante]");
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un plan inactivo no se puede asignar")
    void planInactivoNoSeAsigna() {
        when(clientRepository.findById("c-1")).thenReturn(Optional.of(cliente("c-1", null)));

        assertThatThrownBy(() -> service.assignPlan("c-1", "esmeralda"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Esmeralda está inactivo");
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("Consultar por plan usa el nombre del catalogo, incluso si el plan esta inactivo")
    void consultaPorPlan() {
        List<Client> deOro = List.of(cliente("c-1", "Oro"));
        List<Client> deEsmeralda = List.of(cliente("c-2", "Esmeralda"));
        when(clientRepository.findByPlanIgnoreCase("Oro")).thenReturn(deOro);
        when(clientRepository.findByPlanIgnoreCase("Esmeralda")).thenReturn(deEsmeralda);

        assertThat(service.findByPlan("ORO")).isEqualTo(deOro);
        assertThat(service.findByPlan("esmeralda")).isEqualTo(deEsmeralda);
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
                cliente("c-1", "Oro"), cliente("c-2", null), cliente("c-3", null)));

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

    private static Client cliente(String id, String plan) {
        return Client.builder().clientId(id).userId(id).plan(plan).build();
    }

    private static MarketingPlan plan(String nombre, boolean activo, int orden) {
        return MarketingPlan.builder().planId(UUID.randomUUID()).name(nombre).active(activo)
                .sortOrder(orden).color("#6B7280").createdAt(LocalDateTime.now()).build();
    }
}
