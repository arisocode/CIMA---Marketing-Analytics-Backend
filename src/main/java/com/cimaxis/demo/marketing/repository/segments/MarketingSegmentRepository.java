package com.cimaxis.demo.marketing.repository.segments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.segments.MarketingSegment;

@Repository
public interface MarketingSegmentRepository extends JpaRepository<MarketingSegment, UUID> {

    List<MarketingSegment> findAllByOrderByNameAsc();

    Optional<MarketingSegment> findByNameIgnoreCase(String name);
}
