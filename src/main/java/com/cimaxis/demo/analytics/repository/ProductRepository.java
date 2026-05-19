package com.cimaxis.demo.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.Product;

@Repository
/**
 * Repository JPA para la entidad `PRODUCTS`.
 */
public interface ProductRepository extends JpaRepository<Product, Integer> {
}
