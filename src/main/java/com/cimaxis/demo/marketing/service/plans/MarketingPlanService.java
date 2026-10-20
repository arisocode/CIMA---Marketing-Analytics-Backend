package com.cimaxis.demo.marketing.service.plans;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cimaxis.demo.analytics.repository.ClientRepository;
import com.cimaxis.demo.config.ResourceNotFoundException;
import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;
import com.cimaxis.demo.marketing.dto.plans.PlanRequest;
import com.cimaxis.demo.marketing.dto.plans.PlanResponse;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;

/**
 * Catalogo de planes comerciales. Es la unica fuente de verdad de los planes
 * validos: la asignacion a clientes, la segmentacion y la distribucion de
 * Analitica se validan contra esta tabla.
 */
@Service
public class MarketingPlanService {

    public static final String COLOR_POR_DEFECTO = "#6B7280";
    private static final Pattern COLOR_HEX = Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$");
    private static final int LONGITUD_MAXIMA_NOMBRE = 60;

    private final MarketingPlanRepository planRepository;
    private final ClientRepository clientRepository;

    public MarketingPlanService(MarketingPlanRepository planRepository,
                                ClientRepository clientRepository) {
        this.planRepository = planRepository;
        this.clientRepository = clientRepository;
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public List<PlanResponse> list(boolean includeInactive) {
        Map<String, Long> clientes = clientCountsByPlan();
        return planRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(plan -> includeInactive || Boolean.TRUE.equals(plan.getActive()))
                .map(plan -> toResponse(plan, clientes.getOrDefault(clave(plan.getName()), 0L)))
                .toList();
    }

    /** Planes ordenados por sort_order, activos e inactivos. */
    public List<MarketingPlan> findAllOrdered() {
        return planRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    /** Numero de clientes por plan, con el nombre del plan en minusculas como clave. */
    public Map<String, Long> clientCountsByPlan() {
        Map<String, Long> conteo = new HashMap<>();
        List<Object[]> filas = clientRepository.countGroupedByPlan();
        if (filas == null) {
            return conteo;
        }
        for (Object[] fila : filas) {
            if (fila == null || fila.length < 2 || fila[0] == null) continue;
            long total = fila[1] instanceof Number numero ? numero.longValue() : 0L;
            conteo.merge(clave(fila[0].toString()), total, Long::sum);
        }
        return conteo;
    }

    /** Nombres de los planes activos, en el orden configurado. */
    public List<String> activeNames() {
        return planRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(plan -> Boolean.TRUE.equals(plan.getActive()))
                .map(MarketingPlan::getName)
                .toList();
    }

    // ------------------------------------------------------------------
    // Validaciones usadas por clientes, segmentacion y plantillas
    // ------------------------------------------------------------------

    /**
     * Nombre canonico de un plan que se puede asignar a un cliente.
     * Rechaza planes que no existen o que estan inactivos.
     */
    public String requireAssignable(String valor) {
        MarketingPlan plan = buscar(valor);
        if (!Boolean.TRUE.equals(plan.getActive())) {
            throw new IllegalArgumentException(
                    "El plan " + plan.getName() + " está inactivo y no se puede asignar");
        }
        return plan.getName();
    }

    /** Nombre canonico de un plan existente, activo o inactivo (para filtros y consultas). */
    public String requireExisting(String valor) {
        return buscar(valor).getName();
    }

    /** Como requireAssignable, pero sin lanzar: lo usa la sincronizacion con el CRM. */
    public Optional<String> findAssignable(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        return planRepository.findByNameIgnoreCase(valor.trim())
                .filter(plan -> Boolean.TRUE.equals(plan.getActive()))
                .map(MarketingPlan::getName);
    }

    // ------------------------------------------------------------------
    // Escritura (solo admin, ver MarketingPlanController)
    // ------------------------------------------------------------------

    @Transactional
    public PlanResponse create(PlanRequest request) {
        PlanRequest datos = requerirCuerpo(request);
        String nombre = requerirNombre(datos.name());
        asegurarNombreUnico(nombre, null);

        Integer orden = datos.sortOrder();
        if (orden == null) {
            Integer maximo = planRepository.maxSortOrder();
            orden = (maximo != null ? maximo : 0) + 1;
        }

        MarketingPlan plan = MarketingPlan.builder()
                .planId(UUID.randomUUID())
                .name(nombre)
                .color(normalizarColor(datos.color(), COLOR_POR_DEFECTO))
                .description(textoOpcional(datos.description()))
                .sortOrder(orden)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return toResponse(planRepository.save(plan), 0L);
    }

    /**
     * Edita el plan. Si cambia el nombre, los clientes que tenian el nombre
     * anterior pasan al nuevo en la misma transaccion. color y sortOrder nulos
     * conservan el valor actual.
     */
    @Transactional
    public PlanResponse update(UUID planId, PlanRequest request) {
        PlanRequest datos = requerirCuerpo(request);
        MarketingPlan plan = requerirPlan(planId);
        String nombre = requerirNombre(datos.name());
        asegurarNombreUnico(nombre, planId);

        String nombreAnterior = plan.getName();
        plan.setName(nombre);
        plan.setColor(normalizarColor(datos.color(), plan.getColor()));
        plan.setDescription(textoOpcional(datos.description()));
        if (datos.sortOrder() != null) {
            plan.setSortOrder(datos.sortOrder());
        }
        MarketingPlan guardado = planRepository.save(plan);

        if (!nombre.equals(nombreAnterior)) {
            clientRepository.renamePlan(nombreAnterior, nombre, LocalDateTime.now());
        }

        return toResponse(guardado, clientCountsByPlan().getOrDefault(clave(nombre), 0L));
    }

    /** Activa o desactiva un plan. Los clientes que ya lo tienen lo conservan. */
    @Transactional
    public PlanResponse setActive(UUID planId, Boolean active) {
        if (active == null) {
            throw new IllegalArgumentException("El campo active es obligatorio");
        }
        MarketingPlan plan = requerirPlan(planId);
        plan.setActive(active);
        MarketingPlan guardado = planRepository.save(plan);
        return toResponse(guardado, clientCountsByPlan().getOrDefault(clave(guardado.getName()), 0L));
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Clave para comparar nombres de plan sin distinguir mayusculas ni espacios. */
    public static String clave(String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
    }

    private MarketingPlan buscar(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El plan es obligatorio");
        }
        String nombre = valor.trim();
        return planRepository.findByNameIgnoreCase(nombre)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plan inválido: " + nombre + ". Planes activos: " + activeNames()));
    }

    private MarketingPlan requerirPlan(UUID planId) {
        if (planId == null) {
            throw new IllegalArgumentException("El identificador del plan es obligatorio");
        }
        return planRepository.findById(planId)
                .orElseThrow(() -> ResourceNotFoundException.de("Plan", planId));
    }

    private static PlanRequest requerirCuerpo(PlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Los datos del plan son obligatorios");
        }
        return request;
    }

    private static String requerirNombre(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El nombre del plan es obligatorio");
        }
        String nombre = valor.trim();
        if (nombre.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new IllegalArgumentException(
                    "El nombre del plan admite máximo " + LONGITUD_MAXIMA_NOMBRE + " caracteres");
        }
        return nombre;
    }

    private void asegurarNombreUnico(String nombre, UUID planActual) {
        planRepository.findByNameIgnoreCase(nombre)
                .filter(existente -> !existente.getPlanId().equals(planActual))
                .ifPresent(existente -> {
                    throw new IllegalArgumentException("Ya existe un plan llamado " + existente.getName());
                });
    }

    private static String normalizarColor(String valor, String siVacio) {
        if (valor == null || valor.isBlank()) {
            return siVacio;
        }
        String color = valor.trim();
        if (!COLOR_HEX.matcher(color).matches()) {
            throw new IllegalArgumentException(
                    "Color inválido: " + color + ". Use formato hexadecimal, por ejemplo #D97706");
        }
        return color.toUpperCase(Locale.ROOT);
    }

    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static PlanResponse toResponse(MarketingPlan plan, long clientes) {
        return new PlanResponse(plan.getPlanId(), plan.getName(), plan.getColor(),
                plan.getDescription(), plan.getSortOrder(), plan.getActive(), clientes);
    }
}
