package com.cimaxis.demo.marketing.domain.segments;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Segmento guardado: un nombre para un SegmentCriteria. Las campañas con
 * audiencia SEGMENT lo usan; la lista de clientes se recalcula cada vez.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "marketing_segments")
public class MarketingSegment {

    @Id
    @Column(name = "segment_id")
    private UUID segmentId;

    @Column(name = "name", nullable = false, unique = true, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** JSON de SegmentCriteria (SegmentCriteriaCodec). */
    @Column(name = "criteria", nullable = false, columnDefinition = "TEXT")
    private String criteria;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
