package com.lanka.job.controller;

import com.lanka.job.dto.*;
import com.lanka.job.security.SecurityUtils;
import com.lanka.job.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Real, database-backed job applications.
 *
 * <p>Role gate: WORKER, EMPLOYER or ADMIN (see SecurityConfig). Ownership gate: enforced in
 * {@link ApplicationService} against the {@code uid} claim of the JWT, never against an id the
 * browser claims to own.</p>
 */
@RestController
@RequestMapping("/applications")
@Tag(name = "Job Applications", description = "Apply for a job and manage the applications")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Apply for an open job (WORKER). Duplicate, closed, full and expired jobs are rejected.")
    ApplicationResponse apply(@Valid @RequestBody ApplicationRequest request) {
        return service.apply(request, SecurityUtils.require());
    }

    @GetMapping("/mine")
    @Operation(summary = "Applications of the authenticated caller (worker: own, employer: on own jobs)")
    List<ApplicationResponse> mine() {
        return service.myApplications(SecurityUtils.require());
    }

    @GetMapping("/worker/{workerId}")
    @Operation(summary = "Applications of one worker (that worker or ADMIN)")
    List<ApplicationResponse> byWorker(@PathVariable Long workerId) {
        return service.byWorker(workerId, SecurityUtils.require());
    }

    @GetMapping("/employer/{employerId}")
    @Operation(summary = "Applications received by one employer (that employer or ADMIN)")
    List<ApplicationResponse> byEmployer(@PathVariable Long employerId) {
        return service.byEmployer(employerId, SecurityUtils.require());
    }

    @GetMapping("/job/{jobId}")
    @Operation(summary = "Applications for one job (the job's employer or ADMIN)")
    List<ApplicationResponse> byJob(@PathVariable Long jobId) {
        return service.byJob(jobId, SecurityUtils.require());
    }

    @GetMapping("/summary/worker")
    @Operation(summary = "Real worker dashboard counters (WORKER)")
    WorkerSummaryResponse workerSummary() {
        return service.workerSummary(SecurityUtils.require());
    }

    @GetMapping("/summary/employer")
    @Operation(summary = "Real employer dashboard counters (EMPLOYER or ADMIN)")
    EmployerSummaryResponse employerSummary() {
        return service.employerSummary(SecurityUtils.require());
    }

    @PutMapping("/{id}/accept")
    @Operation(summary = "Accept an applicant (the job's employer or ADMIN). Consumes one job slot.")
    ApplicationResponse accept(@PathVariable Long id) {
        return service.accept(id, SecurityUtils.require());
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Reject an applicant (the job's employer or ADMIN)")
    ApplicationResponse reject(@PathVariable Long id) {
        return service.reject(id, SecurityUtils.require());
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Withdraw an application (the applicant or ADMIN)")
    ApplicationResponse cancel(@PathVariable Long id) {
        return service.cancel(id, SecurityUtils.require());
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "Mark an accepted assignment as completed (the job's employer or ADMIN)")
    ApplicationResponse complete(@PathVariable Long id) {
        return service.complete(id, SecurityUtils.require());
    }
}
