package com.lanka.job.controller;

import com.lanka.job.dto.BrokerPlacementResponse;
import com.lanka.job.dto.EligibleJobResponse;
import com.lanka.job.dto.OfflineWorkerJobRequest;
import com.lanka.job.service.BrokerPlacementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Private job-domain API used by broker-service after it has authorised the broker and worker. */
@RestController
@RequestMapping("/internal/jobs")
@PreAuthorize("hasRole('SYSTEM')")
public class InternalBrokerPlacementController {
    private final BrokerPlacementService service;

    public InternalBrokerPlacementController(BrokerPlacementService service) {
        this.service = service;
    }

    @PostMapping("/eligible")
    List<EligibleJobResponse> eligible(@Valid @RequestBody OfflineWorkerJobRequest worker) {
        return service.eligibleJobs(worker);
    }

    @PostMapping("/{jobId}/broker-placements")
    BrokerPlacementResponse place(@PathVariable Long jobId,
                                  @Valid @RequestBody OfflineWorkerJobRequest worker) {
        return service.place(jobId, worker);
    }

    @GetMapping("/broker-placements")
    List<BrokerPlacementResponse> placements(@RequestParam(required = false) Long brokerEntityId) {
        return service.list(brokerEntityId);
    }
}
