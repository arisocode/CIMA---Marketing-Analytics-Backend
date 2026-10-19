package com.cimaxis.demo.marketing.service.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;
import com.cimaxis.demo.marketing.dto.plans.PlanRequest;
import com.cimaxis.demo.marketing.dto.plans.PlanResponse;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;

/** Catalogo de planes configurables: consulta, creacion, edicion, activacion y validacion. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketingPlanServiceTest {

    @Mock private MarketingPlanRepository planRepository;
    @Mock private ClientRepository clientRepository;

    private MarketingPlanService service;
    private List<MarketingPlan> catalogo;
    private MarketingPlan oro;

    @BeforeEach
    void setUp() {
        oro = plan("Oro", true, 2);
        catalogo = new ArrayList<>(List.of(plan("Platinum", true, 1), oro,
                plan("Diamante", true, 3), plan("Esmeralda", false, 4)));
        when(planRepository.findAllByOrderBySortOrderAscNameAsc()).thenAnswer(inv -> catalogo);
        when(planRepository.findByNameIgnoreCase(anyString())).thenAnswer(inv -> catalogo.stream()
                .filter(p -> p.getName().equalsIgnoreCase(inv.getArgument(0)))
                .findFirst());
        when(planRepository.findById(oro.getPlanId())).thenReturn(Optional.of(oro));
        when(planRepository.save(any(MarketingPlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(planRepository.maxSortOrder()).thenReturn(4);
        when(clientRepository.countGroupedByPlan()).thenReturn(List.of(
                new Object[] {"Oro", 7L}, new Object[] {"oro ", 1L}, new Object[] {"Esmeralda", 2L}));
        service = new MarketingPlanService(planRepository, clientRepository);
    }

    // ---------------------------------------------------------------- consulta

    @Test
    @DisplayName("Lista los activos con su numero de clientes (sin distinguir mayusculas)")
    void listaActivosConClientes() {
        List<PlanResponse> planes = service.list(false);

        assertThat(planes).extracting(PlanResponse::name).containsExactly("Platinum", "Oro", "Diamante");
        assertThat(planes.get(1).clientCount()).isEqualTo(8L);
        assertThat(planes.get(0).clientCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("includeInactive=true incluye los planes desactivados")
    void incluyeInactivos() {
        List<PlanResponse> planes = service.list(true);

        assertThat(planes).extracting(PlanResponse::name).containsExactly("Platinum", "Oro", "Diamante", "Esmeralda");
        assertThat(planes.get(3).active()).isFalse();
        assertThat(planes.get(3).clientCount()).isEqualTo(2L);
    }

    // ---------------------------------------------------------------- creacion

    @Test
    @DisplayName("Crear un plan le asigna id, lo deja activo, normaliza el color y lo pone al final")
    void creaPlan() {
        PlanResponse creado = service.create(new PlanRequest("  Bronce ", "#a16207", " Plan de entrada ", null));

        assertThat(creado.planId()).isNotNull();
        assertThat(creado.name()).isEqualTo("Bronce");
        assertThat(creado.color()).isEqualTo("#A16207");
        assertThat(creado.description()).isEqualTo("Plan de entrada");
        assertThat(creado.sortOrder()).isEqualTo(5);
        assertThat(creado.active()).isTrue();
        assertThat(creado.clientCount()).isZero();
    }

    @Test
    @DisplayName("Sin color se usa el gris por defecto")
    void colorPorDefecto() {
        assertThat(service.create(new PlanRequest("Bronce", null, null, 9)).color())
                .isEqualTo(MarketingPlanService.COLOR_POR_DEFECTO);
    }

    @Test
    @DisplayName("No se permite un nombre repetido, aunque cambien las mayusculas")
    void nombreRepetidoSeRechaza() {
        assertThatThrownBy(() -> service.create(new PlanRequest("ORO", "#D97706", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un plan llamado Oro");
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nombre vacio y color que no es hexadecimal se rechazan")
    void validaNombreYColor() {
        assertThatThrownBy(() -> service.create(new PlanRequest("  ", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del plan es obligatorio");
        assertThatThrownBy(() -> service.create(new PlanRequest("Bronce", "rojo", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Color inválido");
    }

    // ---------------------------------------------------------------- edicion

    @Test
    @DisplayName("Renombrar un plan actualiza en la misma operacion a sus clientes")
    void renombrarActualizaClientes() {
        PlanResponse editado = service.update(oro.getPlanId(), new PlanRequest("Gold", null, "Plan Gold", null));

        assertThat(editado.name()).isEqualTo("Gold");
        assertThat(editado.color()).isEqualTo("#6B7280");
        assertThat(editado.sortOrder()).isEqualTo(2);
        verify(clientRepository).renamePlan(eq("Oro"), eq("Gold"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Editar sin cambiar el nombre no toca a los clientes")
    void editarSinRenombrar() {
        service.update(oro.getPlanId(), new PlanRequest("Oro", "#B45309", null, 7));

        assertThat(oro.getColor()).isEqualTo("#B45309");
        assertThat(oro.getSortOrder()).isEqualTo(7);
        verify(clientRepository, never()).renamePlan(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("No se puede renombrar a un nombre que ya tiene otro plan")
    void renombrarAPlanExistenteSeRechaza() {
        assertThatThrownBy(() -> service.update(oro.getPlanId(), new PlanRequest("diamante", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un plan llamado Diamante");
    }

    @Test
    @DisplayName("Editar un plan que no existe responde recurso no encontrado")
    void editarPlanInexistente() {
        UUID otro = UUID.randomUUID();
        when(planRepository.findById(otro)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(otro, new PlanRequest("Bronce", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- activacion

    @Test
    @DisplayName("Desactivar un plan lo guarda inactivo y conserva sus clientes")
    void desactivarPlan() {
        PlanResponse resultado = service.setActive(oro.getPlanId(), false);

        ArgumentCaptor<MarketingPlan> guardado = ArgumentCaptor.forClass(MarketingPlan.class);
        verify(planRepository).save(guardado.capture());
        assertThat(guardado.getValue().getActive()).isFalse();
        assertThat(resultado.clientCount()).isEqualTo(8L);
        verify(clientRepository, never()).renamePlan(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PATCH active sin valor se rechaza")
    void activeObligatorio() {
        assertThatThrownBy(() -> service.setActive(oro.getPlanId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El campo active es obligatorio");
    }

    // ---------------------------------------------------------------- validacion

    @Test
    @DisplayName("requireAssignable devuelve el nombre canonico y rechaza inactivos o desconocidos")
    void requireAssignable() {
        assertThat(service.requireAssignable(" platinum ")).isEqualTo("Platinum");
        assertThatThrownBy(() -> service.requireAssignable("Esmeralda"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactivo");
        assertThatThrownBy(() -> service.requireAssignable("Bronce"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planes activos: [Platinum, Oro, Diamante]");
    }

    @Test
    @DisplayName("requireExisting acepta planes inactivos (para filtros y consultas)")
    void requireExistingAceptaInactivos() {
        assertThat(service.requireExisting("esmeralda")).isEqualTo("Esmeralda");
    }

    @Test
    @DisplayName("findAssignable no lanza: vacio si el plan no existe o esta inactivo")
    void findAssignable() {
        assertThat(service.findAssignable("ORO")).contains("Oro");
        assertThat(service.findAssignable("Esmeralda")).isEmpty();
        assertThat(service.findAssignable("Bronce")).isEmpty();
        assertThat(service.findAssignable(null)).isEmpty();
    }

    private static MarketingPlan plan(String nombre, boolean activo, int orden) {
        return MarketingPlan.builder().planId(UUID.randomUUID()).name(nombre).active(activo)
                .sortOrder(orden).color("#6B7280").createdAt(LocalDateTime.now()).build();
    }
}
