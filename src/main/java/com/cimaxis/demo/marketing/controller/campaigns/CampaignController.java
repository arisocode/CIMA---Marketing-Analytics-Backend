package com.cimaxis.demo.marketing.controller.campaigns;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.campaigns.CampaignRequest;
import com.cimaxis.demo.marketing.dto.campaigns.CampaignResponse;
import com.cimaxis.demo.marketing.service.campaigns.CampaignService;

/**
 * Endpoints de campanas de marketing.
 */
@RestController
@RequestMapping("/api/v1/marketing/campaigns")
@RequiredArgsConstructor

public class CampaignController {

    private final CampaignService campaignService;

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getAll() {
        return ResponseEntity.ok(campaignService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(campaignService.findById(id));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<CampaignResponse>> getByClient(@PathVariable String clientId) {
        return ResponseEntity.ok(campaignService.findByClient(clientId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<CampaignResponse> create(@RequestBody CampaignRequest request,
                                                   HttpServletRequest servletRequest) {
        String userId = (String) servletRequest.getAttribute("userId");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(campaignService.create(request, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<CampaignResponse> update(@PathVariable Integer id,
                                                   @RequestBody CampaignRequest request) {
        return ResponseEntity.ok(campaignService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        campaignService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
