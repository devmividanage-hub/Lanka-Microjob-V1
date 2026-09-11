package com.lanka.broker.controller;

import com.lanka.broker.dto.*;
import com.lanka.broker.security.AuthPrincipal;
import com.lanka.broker.security.SecurityUtils;
import com.lanka.broker.service.BrokerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/brokers")
@Tag(name = "Brokers", description = "Broker applications, admin review, offline workers and commission")
public class BrokerController {
    private final BrokerService service;

    public BrokerController(BrokerService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Apply to become a broker (public, starts in PENDING state)")
    BrokerResponse apply(@Valid @RequestBody BrokerRequest request) {
        return service.apply(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Broker login; only APPROVED brokers receive a JWT (public)")
    BrokerLoginResponse login(@Valid @RequestBody BrokerLoginRequest request) {
        return service.login(request);
    }

    @GetMapping
    @Operation(summary = "All broker applications (ADMIN only)")
    List<BrokerResponse> list() {
        requireAdmin();
        return service.list();
    }

    @GetMapping("/pending")
    @Operation(summary = "Broker applications waiting for review (ADMIN only)")
    List<BrokerResponse> pending() {
        requireAdmin();
        return service.getPending();
    }

    @GetMapping("/stats")
    @Operation(summary = "Real broker counters and the per-district breakdown (ADMIN only)")
    BrokerStatsResponse stats() {
        requireAdmin();
        return service.stats();
    }

    @GetMapping("/public-stats")
    @Operation(summary = "Approved broker and offline worker counts for the public landing page")
    Map<String, Long> publicStats() {
        return service.publicStats();
    }

    @GetMapping("/workers")
    @Operation(summary = "All offline workers across all brokers (ADMIN only)")
    List<OfflineWorkerResponse> allWorkers() {
        requireAdmin();
        return service.listAllWorkers();
    }

    @GetMapping("/placements")
    @Operation(summary = "All real job placements across all brokers (ADMIN only)")
    List<PlacementRecordResponse> allPlacements() {
        requireAdmin();
        return service.allPlacements();
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Approve a broker application and issue its Broker ID (ADMIN only)")
    BrokerResponse approve(@PathVariable Long id) {
        requireAdmin();
        return service.approve(id);
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject a broker application (ADMIN only)")
    BrokerResponse reject(@PathVariable Long id) {
        requireAdmin();
        return service.reject(id);
    }

    @GetMapping("/{brokerId}/dashboard")
    @Operation(summary = "Broker dashboard with real worker, placement and commission totals (that broker or ADMIN)")
    BrokerDashboardResponse dashboard(@PathVariable String brokerId) {
        return service.getDashboard(brokerId, SecurityUtils.require());
    }

    @GetMapping("/{brokerId}/workers")
    @Operation(summary = "Offline workers managed by this broker (that broker or ADMIN)")
    List<OfflineWorkerResponse> workers(@PathVariable String brokerId) {
        return service.getWorkers(brokerId, SecurityUtils.require());
    }

    @GetMapping("/{brokerId}/placements")
    @Operation(summary = "Database-backed placement history (that broker or ADMIN)")
    List<PlacementRecordResponse> placements(@PathVariable String brokerId) {
        return service.placements(brokerId, SecurityUtils.require());
    }

    @GetMapping("/{brokerId}/workers/{workerId}/eligible-jobs")
    @Operation(summary = "Real OPEN jobs matching the owned ACTIVE worker")
    List<EligibleJobResponse> eligibleJobs(@PathVariable String brokerId, @PathVariable Long workerId) {
        return service.eligibleJobs(brokerId, workerId, SecurityUtils.require());
    }

    @PostMapping("/{brokerId}/workers")
    @Operation(summary = "Register an offline worker; district/city are locked to the broker's assignment")
    OfflineWorkerResponse addWorker(@PathVariable String brokerId, @Valid @RequestBody OfflineWorkerRequest request) {
        return service.addOfflineWorker(brokerId, request, SecurityUtils.require());
    }

    @PutMapping("/{brokerId}/workers/{workerId}/status")
    @Operation(summary = "Change an offline worker's availability (ACTIVE, ON_JOB, INACTIVE)")
    OfflineWorkerResponse updateWorkerStatus(@PathVariable String brokerId, @PathVariable Long workerId,
                                             @RequestParam String status) {
        return service.updateWorkerStatus(brokerId, workerId, status, SecurityUtils.require());
    }

    @PostMapping("/{brokerId}/workers/{workerId}/placements")
    @Operation(summary = "Place an owned ACTIVE worker into a selected real job")
    PlacementResponse recordPlacement(@PathVariable String brokerId, @PathVariable Long workerId,
                                      @Valid @RequestBody PlacementRequest request) {
        return service.recordPlacement(brokerId, workerId, request, SecurityUtils.require());
    }

    private void requireAdmin() {
        AuthPrincipal principal = SecurityUtils.require();
        if (!principal.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can review broker applications");
        }
    }
}
