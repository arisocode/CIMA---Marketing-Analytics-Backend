package com.cimaxis.demo.marketing.dto.commercial;

import java.util.List;

/** Embudo comercial. period null = acumulado historico. conversionPercent[i] = etapa i+1 / etapa i. */
public record FunnelResponse(String period, List<FunnelStage> stages, List<Double> conversionPercent) {
}
