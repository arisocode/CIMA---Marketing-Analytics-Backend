package com.cimaxis.demo.analytics.repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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

    Optional<KpiSnapshot> findByPeriod(String period);

    List<KpiSnapshot> findByPeriodBetweenOrderByPeriodAsc(String from, String to);
}
