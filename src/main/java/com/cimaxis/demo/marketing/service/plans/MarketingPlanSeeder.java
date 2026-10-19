package com.cimaxis.demo.marketing.service.plans;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;

/**
 * Siembra los planes historicos (Platinum, Oro y Diamante) cuando la tabla
 * marketing_plans esta vacia. Corre al arrancar, antes de la sincronizacion
 * con el CRM; si la tabla ya tiene planes no hace nada.
 */
@Component
public class MarketingPlanSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketingPlanSeeder.class);

    /** Planes iniciales. Los colores coinciden con los chips del frontend. */
    static final List<PlanInicial> PLANES_INICIALES = List.of(
            new PlanInicial("Platinum", "#64748B", "Plan comercial Platinum del portafolio de CIMA.", 1),
            new PlanInicial("Oro", "#D97706", "Plan comercial Oro del portafolio de CIMA.", 2),
            new PlanInicial("Diamante", "#0284C7", "Plan comercial Diamante del portafolio de CIMA.", 3));

    record PlanInicial(String name, String color, String description, int sortOrder) {
    }

    private final MarketingPlanRepository planRepository;

    public MarketingPlanSeeder(MarketingPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int creados = seedIfEmpty();
            if (creados > 0) {
                log.info("Planes iniciales sembrados en marketing_plans: {}", creados);
            }
        } catch (Exception e) {
            log.error("No se pudieron sembrar los planes iniciales: {}", e.getMessage(), e);
        }
    }

    /** Devuelve cuantos planes creo (0 si la tabla ya tenia datos). */
    public int seedIfEmpty() {
        if (planRepository.count() > 0) {
            return 0;
        }
        LocalDateTime ahora = LocalDateTime.now();
        List<MarketingPlan> planes = PLANES_INICIALES.stream()
                .map(inicial -> MarketingPlan.builder()
                        .planId(UUID.randomUUID())
                        .name(inicial.name())
                        .color(inicial.color())
                        .description(inicial.description())
                        .sortOrder(inicial.sortOrder())
                        .active(true)
                        .createdAt(ahora)
                        .build())
                .toList();
        planRepository.saveAll(planes);
        return planes.size();
    }
}
