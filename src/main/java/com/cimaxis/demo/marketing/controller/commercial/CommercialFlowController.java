package com.cimaxis.demo.marketing.controller.commercial;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.commercial.ClientTimelineResponse;
import com.cimaxis.demo.marketing.dto.commercial.FunnelClientResponse;
import com.cimaxis.demo.marketing.dto.commercial.FunnelResponse;
import com.cimaxis.demo.marketing.dto.commercial.TodayResponse;
import com.cimaxis.demo.marketing.service.commercial.ClientTimelineService;
import com.cimaxis.demo.marketing.service.commercial.FunnelService;
import com.cimaxis.demo.marketing.service.commercial.TodayPanelService;

/**
 * Flujo comercial (admin y worker): embudo, ficha 360° del cliente y panel Hoy.
 */
@RestController
@RequestMapping("/api/v1/marketing")
@PreAuthorize("hasAnyRole('admin','worker')")
public class CommercialFlowController {

    private final FunnelService funnelService;
    private final ClientTimelineService timelineService;
    private final TodayPanelService todayPanelService;

    public CommercialFlowController(FunnelService funnelService,
                                    ClientTimelineService timelineService,
                                    TodayPanelService todayPanelService) {
        this.funnelService = funnelService;
        this.timelineService = timelineService;
        this.todayPanelService = todayPanelService;
    }

    /** Sin period = acumulado historico. */
    @GetMapping("/funnel")
    public ResponseEntity<FunnelResponse> funnel(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(funnelService.funnel(period));
    }

    @GetMapping("/funnel/{stageKey}/clients")
    public ResponseEntity<List<FunnelClientResponse>> stageClients(@PathVariable String stageKey,
                                                                   @RequestParam(required = false) String period) {
        return ResponseEntity.ok(funnelService.stageClients(stageKey, period));
    }

    @GetMapping("/clients/{clientId}/timeline")
    public ResponseEntity<ClientTimelineResponse> timeline(@PathVariable String clientId) {
        return ResponseEntity.ok(timelineService.timeline(clientId));
    }

    @GetMapping("/today")
    public ResponseEntity<TodayResponse> today(
            @RequestParam(defaultValue = "15") Integer daysWithoutContact,
            @RequestParam(defaultValue = "7") Integer daysWithoutResponse) {
        return ResponseEntity.ok(todayPanelService.today(daysWithoutContact, daysWithoutResponse));
    }
}
