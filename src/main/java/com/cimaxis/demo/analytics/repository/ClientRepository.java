package com.cimaxis.demo.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
     * Cuenta clientes por plan.
     */
    long countByPlan(Client.Plan plan);

    /**
     * Me dice cuantos usuarios creados hay entre una fecha y otra.
     */
    long countByCreatedAtBetween(LocalDateTime desde, LocalDateTime hasta);

    List<Client> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Client> findByPlan(Client.Plan plan);
}
