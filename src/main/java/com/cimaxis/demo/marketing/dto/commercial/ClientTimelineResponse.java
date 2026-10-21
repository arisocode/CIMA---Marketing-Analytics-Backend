package com.cimaxis.demo.marketing.dto.commercial;

import java.util.List;

public record ClientTimelineResponse(TimelineClient client, TimelineStats stats, List<TimelineEvent> events) {
}
