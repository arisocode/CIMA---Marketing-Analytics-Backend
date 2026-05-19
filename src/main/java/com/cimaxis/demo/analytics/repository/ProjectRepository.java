package com.cimaxis.demo.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.Project;

@Repository
/**
 * Repository JPA para la entidad `PROJECTS`.
 */
public interface ProjectRepository extends JpaRepository<Project, Integer> {

    /**
     * Cuenta proyectos por su estado (p.ej. 'In Progress').
     */
    long countByStatus(String status);

    /**
     * Cuenta proyectos por cliente.
     */
    long countByClientId(Integer clientId);
}
