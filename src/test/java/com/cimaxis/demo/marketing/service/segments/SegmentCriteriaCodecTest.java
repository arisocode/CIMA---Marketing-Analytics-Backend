package com.cimaxis.demo.marketing.service.segments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cimaxis.demo.marketing.service.segmentation.SegmentCriteria;

/** Criterio de segmento guardado como JSON en marketing_segments.criteria. */
class SegmentCriteriaCodecTest {

    @Test
    @DisplayName("Ida y vuelta conserva todos los campos, incluidos textos con comillas y tildes")
    void idaYVuelta() {
        SegmentCriteria original = SegmentCriteria.builder().plans(List.of("Oro", "Diamante \"VIP\""))
                .hasProjects(true).hasInteractions(false).minDaysWithoutContact(15)
                .proposalStatuses(List.of("Sent", "In_negotiation")).build();

        String json = SegmentCriteriaCodec.toJson(original);
        SegmentCriteria leido = SegmentCriteriaCodec.fromJson(json);

        assertThat(json).isEqualTo("{\"plans\":[\"Oro\",\"Diamante \\\"VIP\\\"\"],\"hasProjects\":true,"
                + "\"hasInteractions\":false,\"minDaysWithoutContact\":15,\"proposalStatuses\":[\"Sent\",\"In_negotiation\"]}");
        assertThat(leido.getPlans()).containsExactly("Oro", "Diamante \"VIP\"");
        assertThat(leido.getHasProjects()).isTrue();
        assertThat(leido.getHasInteractions()).isFalse();
        assertThat(leido.getMinDaysWithoutContact()).isEqualTo(15);
        assertThat(leido.getProposalStatuses()).containsExactly("Sent", "In_negotiation");
    }

    @Test
    @DisplayName("Criterio vacío = todos los clientes; campos desconocidos se ignoran")
    void vacioYDesconocidos() {
        assertThat(SegmentCriteriaCodec.toJson(new SegmentCriteria())).isEqualTo("{}");
        SegmentCriteria leido = SegmentCriteriaCodec.fromJson(" { \"otro\": [1, 2.5, null], \"hasProjects\": true } ");
        assertThat(leido.getHasProjects()).isTrue();
        assertThat(leido.getPlans()).isNull();
        assertThat(SegmentCriteriaCodec.fromJson(null).getPlans()).isNull();
    }

    @Test
    @DisplayName("JSON mal formado responde 400")
    void malFormado() {
        assertThatThrownBy(() -> SegmentCriteriaCodec.fromJson("{\"plans\": ["))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> SegmentCriteriaCodec.fromJson("[1,2]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objeto JSON");
    }
}
