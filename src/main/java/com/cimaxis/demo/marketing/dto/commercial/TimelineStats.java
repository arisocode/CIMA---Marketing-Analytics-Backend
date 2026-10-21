package com.cimaxis.demo.marketing.dto.commercial;

import java.math.BigDecimal;

public record TimelineStats(long interactions, long responses, long proposals,
                            BigDecimal approvedValue, long projects) {
}
