package com.cimaxis.demo.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.Client;

@Repository
/**
 * Repository JPA para operaciones sobre `CLIENTS`.
 * Contiene consultas específicas usadas por el módulo de analytics.
 */
public interface ClientRepository extends JpaRepository<Client, Integer> {

    /**
     * Cuenta clientes por plan.
     */
    long countByPlan(Client.Plan plan);
}
