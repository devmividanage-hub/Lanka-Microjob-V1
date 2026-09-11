package com.lanka.job.service;

import com.lanka.job.dto.BrokerPlacementResponse;
import com.lanka.job.dto.EligibleJobResponse;
import com.lanka.job.dto.OfflineWorkerJobRequest;
import com.lanka.job.model.BrokerPlacement;
import com.lanka.job.model.Job;
import com.lanka.job.repository.BrokerPlacementRepository;
import com.lanka.job.repository.JobRepository;
import com.lanka.job.repository.JobSpecifications;
import com.lanka.job.security.AuthPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Atomic job-slot reservation and durable record for broker-managed offline workers. */
@Service
public class BrokerPlacementService {
    private static final Set<String> NO_EXPERIENCE = Set.of(
            "no experience", "no experience needed", "no experience required", "none");

    private final JobRepository jobs;
    private final BrokerPlacementRepository placements;
    private final double commissionRate;

    public BrokerPlacementService(JobRepository jobs, BrokerPlacementRepository placements,
                                  @Value("${app.broker.commission-rate:0.075}") double commissionRate) {
        this.jobs = jobs;
        this.placements = placements;
        this.commissionRate = commissionRate;
    }

    /** Returns only jobs the worker can actually be placed into. */
    @Transactional(readOnly = true)
    public List<EligibleJobResponse> eligibleJobs(OfflineWorkerJobRequest worker) {
        Specification<Job> spec = Specification
                .where(JobSpecifications.statusIn(List.of(JobLifecycle.OPEN)))
                .and(JobSpecifications.district(worker.district()))
                .and(JobSpecifications.notExpired(LocalDate.now()))
                .and(JobSpecifications.hasFreeSlots());
        return jobs.findAll(spec).stream()
                .filter(job -> cityMatches(job, worker))
                .filter(job -> skillsMatch(job, worker))
                .filter(job -> !placements.existsByOfflineWorkerIdAndJobId(worker.workerId(), job.getId()))
                .map(this::toEligible)
                .toList();
    }

    /**
     * Re-validates against a pessimistically locked Job, then creates the placement and consumes a
     * slot in one database transaction. The browser never supplies pay or commission.
     */
    @Transactional
    public BrokerPlacementResponse place(Long jobId, OfflineWorkerJobRequest worker) {
        Job job = jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        validate(job, worker);
        if (placements.existsByOfflineWorkerIdAndJobId(worker.workerId(), jobId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This worker is already placed on this job");
        }

        long commission = Math.round(job.getPayPerWorker() * commissionRate);
        BrokerPlacement placement = new BrokerPlacement();
        placement.setBrokerEntityId(worker.brokerEntityId());
        placement.setBrokerId(worker.brokerId());
        placement.setOfflineWorkerId(worker.workerId());
        placement.setWorkerName(worker.workerName());
        placement.setJob(job);
        placement.setEmployerId(job.getEmployerId());
        placement.setEmployer(job.getEmployer());
        placement.setPayPerDay(job.getPayPerWorker());
        placement.setCommissionAmount(commission);
        BrokerPlacement saved = placements.saveAndFlush(placement);

        int remaining = job.getSlotsRemaining() - 1;
        job.setSlotsRemaining(remaining);
        if (remaining == 0) {
            job.setStatus(JobLifecycle.ASSIGNED);
        }
        jobs.save(job);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BrokerPlacementResponse> list(Long brokerEntityId) {
        List<BrokerPlacement> records = brokerEntityId == null
                ? placements.findAllByOrderByIdDesc()
                : placements.findByBrokerEntityIdOrderByIdDesc(brokerEntityId);
        return records.stream().map(this::toResponse).toList();
    }

    /** Placements belonging to jobs posted by the authenticated employer. */
    @Transactional(readOnly = true)
    public List<BrokerPlacementResponse> mine(AuthPrincipal principal) {
        requireEmployer(principal);
        List<BrokerPlacement> records = principal.isAdmin()
                ? placements.findAllByOrderByIdDesc()
                : placements.findByEmployerIdOrderByIdDesc(principal.uid());
        return records.stream().map(this::toResponse).toList();
    }

    /** Only the job owner (or ADMIN) can create or change this placement rating. */
    @Transactional
    public BrokerPlacementResponse rate(Long placementId, Integer rating, AuthPrincipal principal) {
        requireEmployer(principal);
        BrokerPlacement placement = placements.findById(placementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Placement not found"));
        Long employerId = placement.getJob().getEmployerId();
        if (!principal.isAdmin() && (employerId == null || !principal.owns(employerId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only rate workers placed on jobs you posted");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }
        placement.setRating(rating);
        placement.setRatedAt(LocalDateTime.now());
        return toResponse(placements.save(placement));
    }

    private void validate(Job job, OfflineWorkerJobRequest worker) {
        if (!JobLifecycle.isOpen(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an OPEN job can accept a placement");
        }
        if (job.getSlotsRemaining() == null || job.getSlotsRemaining() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This job has no available slots");
        }
        if (job.getJobDate() != null && job.getJobDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This job date has already passed");
        }
        if (!same(job.getDistrict(), worker.district())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The worker and job districts do not match");
        }
        if (!cityMatches(job, worker)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The worker and job cities do not match");
        }
        if (!skillsMatch(job, worker)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The worker does not have a required job skill");
        }
        if (job.getPayPerWorker() == null || job.getPayPerWorker() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The job has no valid pay amount");
        }
    }

    private void requireEmployer(AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!principal.hasRole("EMPLOYER", "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an employer can view or rate placed workers");
        }
    }

    /** A populated job city is a real city restriction; otherwise district match is sufficient. */
    private boolean cityMatches(Job job, OfflineWorkerJobRequest worker) {
        return blank(job.getCity()) || same(job.getCity(), worker.city());
    }

    /** At least one real required skill must match; jobs with no skill requirement remain eligible. */
    private boolean skillsMatch(Job job, OfflineWorkerJobRequest worker) {
        Set<String> required = skills(job.getRequiredSkills());
        if (required.isEmpty() || required.stream().allMatch(NO_EXPERIENCE::contains)) return true;
        Set<String> available = skills(worker.skills());
        return required.stream().anyMatch(available::contains);
    }

    private Set<String> skills(String value) {
        Set<String> values = new LinkedHashSet<>();
        if (blank(value)) return values;
        Arrays.stream(value.split(","))
                .map(String::trim).filter(skill -> !skill.isEmpty())
                .map(String::toLowerCase).forEach(values::add);
        return values;
    }

    private boolean same(String left, String right) {
        return !blank(left) && !blank(right) && left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private EligibleJobResponse toEligible(Job job) {
        return new EligibleJobResponse(job.getId(), job.getTitle(), job.getEmployer(), job.getEmployerId(),
                job.getCategory(), job.getDistrict(), job.getCity(), job.getPayPerWorker(), job.getJobDate(),
                JobLifecycle.normalize(job.getStatus()), job.getSlotsRemaining(), job.getRequiredSkills());
    }

    private BrokerPlacementResponse toResponse(BrokerPlacement placement) {
        Job job = placement.getJob();
        return new BrokerPlacementResponse(placement.getId(), placement.getBrokerEntityId(),
                placement.getBrokerId(), placement.getOfflineWorkerId(), placement.getWorkerName(), job.getId(),
                job.getTitle(), placement.getEmployerId(), placement.getEmployer(), job.getCategory(),
                job.getDistrict(), job.getCity(), placement.getPayPerDay(), placement.getCommissionAmount(),
                placement.getPlacementDate(), placement.getStatus(), job.getSlotsRemaining(),
                JobLifecycle.normalize(job.getStatus()), placement.getRating(), placement.getRatedAt());
    }
}
