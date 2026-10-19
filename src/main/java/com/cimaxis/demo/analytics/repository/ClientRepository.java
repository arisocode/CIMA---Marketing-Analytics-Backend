package com.cimaxis.demo.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.Client;

import java.time.LocalDateTime;
import java.util.List;

@Repository
/**
 * Repository JPA para operaciones sobre `CLIENTS`.
 * Contiene consultas específicas usadas por el módulo de analytics.
 */
public interface ClientRepository extends JpaRepository<Client, String> {

    /**
     * Me dice cuantos usuarios creados hay entre una fecha y otra.
     */
    long countByCreatedAtBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Client> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    /** Clientes de un plan, sin distinguir mayusculas. */
    List<Client> findByPlanIgnoreCase(String plan);

    /** Numero de clientes por plan: filas [plan, total]. */
    @Query("SELECT c.plan, COUNT(c) FROM Client c WHERE c.plan IS NOT NULL GROUP BY c.plan")
    List<Object[]> countGroupedByPlan();

    /**
     * Pasa a los clientes de un plan renombrado al nombre nuevo. Se usa en la
     * misma transaccion que guarda el plan (MarketingPlanService.update).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Client c SET c.plan = :nuevo, c.updatedAt = :ahora WHERE LOWER(c.plan) = LOWER(:anterior)")
    int renamePlan(@Param("anterior") String anterior,
                   @Param("nuevo") String nuevo,
                   @Param("ahora") LocalDateTime ahora);

    /**
     * Consulta atómica agregada para obtener métricas de clientes sin incurrir en N+1 queries.
     */
    @Query("""
        SELECT c.clientId AS clientId,
               c.plan AS plan,
               (SELECT COUNT(cam) FROM com.cimaxis.demo.marketing.domain.campaigns.Campaign cam WHERE cam.clientId = c.clientId) AS campaignCount,
               (SELECT COUNT(p) FROM com.cimaxis.demo.analytics.domain.Project p WHERE p.clientId = c.clientId) AS projectCount
        FROM Client c
    """)
    java.util.List<com.cimaxis.demo.analytics.dto.ClientActivitySummaryProjection> findClientActivitiesSummary();
}
