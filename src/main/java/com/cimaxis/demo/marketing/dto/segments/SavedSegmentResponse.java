package com.cimaxis.demo.marketing.dto.segments;

import java.util.UUID;

import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;

/** clientCount = clientes que hoy cumplen el criterio (se recalcula en cada consulta). */
public record SavedSegmentResponse(UUID segmentId, String name, String description, SegmentCriteria criteria,
                                   Boolean active, long clientCount) {
}
