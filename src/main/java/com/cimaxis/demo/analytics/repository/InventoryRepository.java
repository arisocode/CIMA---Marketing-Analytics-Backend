package com.cimaxis.demo.analytics.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.Inventory;

@Repository
/**
 * Repository JPA para la entidad `INVENTORY`.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Integer> {

    /**
     * Encuentra registros cuyo `total_stock` es menor o igual a un umbral.
     */
    List<Inventory> findByTotalStockLessThanEqual(Integer threshold);
}
