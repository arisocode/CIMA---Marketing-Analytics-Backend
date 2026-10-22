package com.cimaxis.demo.marketing.dto.segments;

import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;

/** Segmento guardado: criteria es el mismo objeto que recibe /segments/preview. */
public record SavedSegmentRequest(String name, String description, SegmentCriteria criteria) {
}
