package com.cimaxis.demo.marketing.controller.campaigns;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cimaxis.demo.marketing.dto.campaigns.ProposalRequest;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalResponse;
import com.cimaxis.demo.marketing.dto.campaigns.ProposalStatusRequest;
import com.cimaxis.demo.marketing.service.campaigns.ProposalService;

/**
 * Endpoints de propuestas comerciales.
 */
@RestController
@RequestMapping("/api/v1/marketing/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService proposalService;

    @GetMapping
    public ResponseEntity<List<ProposalResponse>> getAll() {
        return ResponseEntity.ok(proposalService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProposalResponse> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(proposalService.findById(id));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<ProposalResponse>> getByClient(@PathVariable String clientId) {
        return ResponseEntity.ok(proposalService.findByClient(clientId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<ProposalResponse>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(proposalService.findByStatus(status));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<ProposalResponse> create(@RequestBody ProposalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proposalService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<ProposalResponse> update(@PathVariable Integer id,
                                                   @RequestBody ProposalRequest request) {
        return ResponseEntity.ok(proposalService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('admin','worker')")
    public ResponseEntity<ProposalResponse> changeStatus(@PathVariable Integer id,
                                                         @RequestBody ProposalStatusRequest request) {
        return ResponseEntity.ok(proposalService.changeStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        proposalService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
