package com.cimaxis.demo.analytics.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.KpiSnapshot;

@Repository
/**
 * Repository JPA para `KPI_SNAPSHOTS`.
 */
public interface KpiSnapshotRepository extends JpaRepository<KpiSnapshot, Integer> {

    /**
     * Devuelve todos los snapshots ordenados por fecha de cálculo descendente.
     */
    List<KpiSnapshot> findAllByOrderByCalculatedAtDesc();
}
