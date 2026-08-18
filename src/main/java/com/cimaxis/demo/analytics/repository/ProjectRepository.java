package com.cimaxis.demo.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.Project;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
/**
 * Repository JPA para la entidad `PROJECTS`.
 */
public interface ProjectRepository extends JpaRepository<Project, String> {

    /**
     * Cuenta proyectos por su estado (p.ej. 'In Progress').
     */
    long countByStatus(String status);

    /**
     * Cuenta proyectos por cliente.
     */
    long countByClientId(String clientId);

    List<Project> findByClientId(String clientId);

    @Query("SELECT p FROM Project p WHERE LOWER(p.status) IN :statuses")
    List<Project> findByStatusIn(@Param("statuses") Collection<String> statuses);

    @Query("SELECT COUNT(p) FROM Project p WHERE LOWER(p.status) IN :statuses")
    long countByStatusIn(@Param("statuses") Collection<String> statuses);

    @Query("SELECT p FROM Project p WHERE LOWER(p.status) IN :statuses "
            + "AND p.updatedAt BETWEEN :from AND :to")
    List<Project> findByStatusInAndUpdatedAtBetween(@Param("statuses") Collection<String> statuses,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);
}
