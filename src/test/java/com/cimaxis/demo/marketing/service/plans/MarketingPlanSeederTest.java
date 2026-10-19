package com.cimaxis.demo.marketing.service.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cimaxis.demo.marketing.domain.plans.MarketingPlan;
import com.cimaxis.demo.marketing.repository.plans.MarketingPlanRepository;

@ExtendWith(MockitoExtension.class)
class MarketingPlanSeederTest {

    @Mock private MarketingPlanRepository planRepository;

    @Test
    @SuppressWarnings("unchecked")
    void siembraPlatinumOroYDiamanteSiLaTablaEstaVacia() {
        when(planRepository.count()).thenReturn(0L);

        int creados = new MarketingPlanSeeder(planRepository).seedIfEmpty();

        ArgumentCaptor<List<MarketingPlan>> planes = ArgumentCaptor.forClass(List.class);
        verify(planRepository).saveAll(planes.capture());
        assertThat(creados).isEqualTo(3);
        assertThat(planes.getValue()).extracting(MarketingPlan::getName)
                .containsExactly("Platinum", "Oro", "Diamante");
        assertThat(planes.getValue()).allMatch(p -> Boolean.TRUE.equals(p.getActive()) && p.getPlanId() != null);
    }

    @Test
    void noSiembraSiYaHayPlanes() {
        when(planRepository.count()).thenReturn(4L);

        assertThat(new MarketingPlanSeeder(planRepository).seedIfEmpty()).isZero();
        verify(planRepository, never()).saveAll(any());
    }
}
