package com.lanka.job.controller;

import com.lanka.job.dto.JobRequest;
import com.lanka.job.dto.JobResponse;
import com.lanka.job.dto.BrokerPlacementResponse;
import com.lanka.job.dto.PlacementRatingRequest;
import com.lanka.job.security.SecurityUtils;
import com.lanka.job.service.JobService;
import com.lanka.job.service.BrokerPlacementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/jobs")
@Tag(name = "Jobs", description = "Job posting, the public worker feed and the job lifecycle")
public class JobController {
    private final JobService service;
    private final BrokerPlacementService placements;

    public JobController(JobService service, BrokerPlacementService placements) {
        this.service = service;
        this.placements = placements;
    }

    @PostMapping
    @Operation(summary = "Post a job (EMPLOYER or ADMIN). The employer is taken from the JWT.")
    JobResponse create(@Valid @RequestBody JobRequest request) {
        return service.create(request, SecurityUtils.require());
    }

    @GetMapping
    @Operation(summary = "Public job feed with district / city / category / text filters (no auth needed)")
    List<JobResponse> list(@RequestParam(required = false) String district,
                           @RequestParam(required = false) String city,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String search,
                           @RequestParam(required = false, defaultValue = "false") boolean includeClosed) {
        return service.search(district, city, category, search, includeClosed);
    }

    @GetMapping("/mine")
    @Operation(summary = "Jobs posted by the authenticated employer, with application counts (EMPLOYER or ADMIN)")
    List<JobResponse> mine(@RequestParam(required = false) String status) {
        return service.myJobs(SecurityUtils.require(), status);
    }

    @GetMapping("/placements/mine")
    @Operation(summary = "Broker-managed workers placed on the authenticated employer's jobs")
    List<BrokerPlacementResponse> myPlacements() {
        return placements.mine(SecurityUtils.require());
    }

    @PutMapping("/placements/{placementId}/rating")
    @Operation(summary = "Rate a broker-managed worker placed on the authenticated employer's job")
    BrokerPlacementResponse ratePlacement(@PathVariable Long placementId,
                                          @Valid @RequestBody PlacementRatingRequest request) {
        return placements.rate(placementId, request.rating(), SecurityUtils.require());
    }

    @GetMapping("/employer/{employerId}")
    @Operation(summary = "Jobs of one employer (that employer or ADMIN only)")
    List<JobResponse> byEmployer(@PathVariable Long employerId) {
        return service.jobsOfEmployer(employerId, SecurityUtils.require());
    }

    @GetMapping("/stats")
    @Operation(summary = "Real platform job counters (any authenticated role)")
    Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/public-stats")
    @Operation(summary = "Total job count for the public landing page")
    Map<String, Long> publicStats() {
        return service.publicStats();
    }

    @GetMapping("/admin")
    @Operation(summary = "Complete job directory with application counts (ADMIN only)")
    List<JobResponse> adminList() {
        return service.listForAdmin(SecurityUtils.require());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Job details (public)")
    JobResponse get(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Move a job through its lifecycle (job owner or ADMIN)")
    JobResponse updateStatus(@PathVariable Long id, @RequestParam String status) {
        return service.updateStatus(id, status, SecurityUtils.require());
    }

    @PutMapping("/{id}/flag")
    @Operation(summary = "Flag a job for moderation (ADMIN only)")
    JobResponse flag(@PathVariable Long id) {
        return service.flagJob(id, SecurityUtils.require());
    }
}
