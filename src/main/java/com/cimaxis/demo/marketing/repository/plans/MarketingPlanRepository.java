package com.cimaxis.demo.marketing.repository.plans;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;

@Repository
public interface MarketingPlanRepository extends JpaRepository<MarketingPlan, UUID> {

    List<MarketingPlan> findAllByOrderBySortOrderAscNameAsc();

    Optional<MarketingPlan> findByNameIgnoreCase(String name);

    @Query("SELECT COALESCE(MAX(p.sortOrder), 0) FROM MarketingPlan p")
    Integer maxSortOrder();
}
