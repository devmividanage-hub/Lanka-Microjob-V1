package com.lanka.job.service;

import com.lanka.job.client.NotificationClient;
import com.lanka.job.dto.*;
import com.lanka.job.model.Job;
import com.lanka.job.model.JobApplication;
import com.lanka.job.repository.JobApplicationRepository;
import com.lanka.job.repository.JobRepository;
import com.lanka.job.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Database-backed job applications.
 *
 * <p>Every rule the brief asks for is enforced here on the server: a worker can only apply to an
 * open, not-full, not-expired job and only once; an employer can only see and decide on
 * applications for jobs they own; a worker can only cancel their own application. Ids in the URL
 * are always checked against the identity carried by the JWT.</p>
 */
@Service
public class ApplicationService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    public static final String APPLIED = "APPLIED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String COMPLETED = "COMPLETED";

    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final JobService jobService;
    private final NotificationClient notifications;

    public ApplicationService(JobApplicationRepository applications, JobRepository jobs, JobService jobService,
                              NotificationClient notifications) {
        this.applications = applications;
        this.jobs = jobs;
        this.jobService = jobService;
        this.notifications = notifications;
    }

    @Transactional
    public ApplicationResponse apply(ApplicationRequest request, AuthPrincipal principal) {
        requireWorker(principal);
        Job job = jobService.findEntity(request.jobId());

        if (!JobLifecycle.isOpen(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, JobLifecycle.describeBlockedApplication(job.getStatus()));
        }
        if (job.getSlotsRemaining() != null && job.getSlotsRemaining() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This job is full - all slots are taken");
        }
        if (job.getJobDate() != null && job.getJobDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This job has expired");
        }
        if (applications.existsByJobIdAndWorkerId(job.getId(), principal.uid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied for this job");
        }

        JobApplication application = new JobApplication();
        application.setJob(job);
        application.setWorkerId(principal.uid());
        application.setEmployerId(job.getEmployerId() == null ? 0L : job.getEmployerId());
        application.setWorkerName(principal.name());
        application.setWorkerEmail(principal.identifier() != null && principal.identifier().contains("@")
                ? principal.identifier() : null);
        application.setWorkerSkills(principal.skills());
        application.setMessage(request.message() == null || request.message().isBlank() ? null : request.message().trim());
        application.setStatus(APPLIED);

        JobApplication saved;
        try {
            saved = applications.saveAndFlush(application);
        } catch (DataIntegrityViolationException ex) {
            // Unique (job_id, worker_id) constraint: lost a race with a duplicate submission.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already applied for this job");
        }

        notifications.notifyAuto(job.getEmployerEmail(), "APPLICATION_SUBMITTED",
                (saved.getWorkerName() == null ? "A worker" : saved.getWorkerName())
                        + " applied for your job '" + job.getTitle() + "'.");
        notifications.notifyAuto(saved.getWorkerEmail(), "APPLICATION_SUBMITTED",
                "We received your application for '" + job.getTitle() + "'. The employer has been notified.");
        log.info("Worker {} applied to job {} (application {})", principal.uid(), job.getId(), saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> myApplications(AuthPrincipal principal) {
        AuthPrincipal current = requirePrincipal(principal);
        if (current.hasRole("EMPLOYER")) {
            return applications.findByEmployerIdOrderByIdDesc(current.uid()).stream().map(this::toResponse).toList();
        }
        if (current.hasRole("WORKER")) {
            return applications.findByWorkerIdOrderByIdDesc(current.uid()).stream().map(this::toResponse).toList();
        }
        if (current.isAdmin()) {
            return applications.findAll().stream().map(this::toResponse).toList();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role cannot list applications");
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> byWorker(Long workerId, AuthPrincipal principal) {
        AuthPrincipal current = requirePrincipal(principal);
        if (!current.isAdmin() && !current.owns(workerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own applications");
        }
        return applications.findByWorkerIdOrderByIdDesc(workerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> byEmployer(Long employerId, AuthPrincipal principal) {
        AuthPrincipal current = requirePrincipal(principal);
        if (!current.isAdmin() && !current.owns(employerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view applications for your own jobs");
        }
        return applications.findByEmployerIdOrderByIdDesc(employerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> byJob(Long jobId, AuthPrincipal principal) {
        Job job = jobService.findEntity(jobId);
        requireJobOwner(job, principal);
        return applications.findByJobIdOrderByIdDesc(jobId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ApplicationResponse accept(Long id, AuthPrincipal principal) {
        JobApplication application = findApplication(id);
        requireJobOwner(application.getJob(), principal);
        // Use the same row lock as broker placements so two accepted workers can never consume the
        // final slot concurrently.
        Job job = jobs.findByIdForUpdate(application.getJob().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if (!APPLIED.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a pending application can be accepted (current status: " + application.getStatus() + ")");
        }
        if (JobLifecycle.isTerminal(job.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot accept an application on a job that is " + JobLifecycle.normalize(job.getStatus()));
        }
        if (job.getSlotsRemaining() != null && job.getSlotsRemaining() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This job is full - all slots are taken");
        }

        application.setStatus(ACCEPTED);
        application.setUpdatedAt(LocalDateTime.now());
        JobApplication saved = applications.save(application);

        int remaining = (job.getSlotsRemaining() == null ? 1 : job.getSlotsRemaining()) - 1;
        job.setSlotsRemaining(Math.max(remaining, 0));
        if (job.getSlotsRemaining() == 0 && JobLifecycle.isOpen(job.getStatus())) {
            // All slots are filled: the job moves on in its lifecycle.
            job.setStatus(JobLifecycle.ASSIGNED);
        }
        jobs.save(job);

        notifications.notifyAuto(saved.getWorkerEmail(), "APPLICATION_ACCEPTED",
                "Good news! You were accepted for '" + job.getTitle() + "' on " + job.getJobDate() + ".");
        notifications.notifyAuto(job.getEmployerEmail(), "WORKER_ACCEPTED",
                "You accepted " + (saved.getWorkerName() == null ? "a worker" : saved.getWorkerName())
                        + " for '" + job.getTitle() + "'. Slots remaining: " + job.getSlotsRemaining() + ".");
        log.info("Application {} accepted for job {} by {}", id, job.getId(), principal.uid());
        return toResponse(saved);
    }

    @Transactional
    public ApplicationResponse reject(Long id, AuthPrincipal principal) {
        JobApplication application = findApplication(id);
        requireJobOwner(application.getJob(), principal);
        if (!APPLIED.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a pending application can be rejected (current status: " + application.getStatus() + ")");
        }
        application.setStatus(REJECTED);
        application.setUpdatedAt(LocalDateTime.now());
        JobApplication saved = applications.save(application);
        notifications.notifyAuto(saved.getWorkerEmail(), "APPLICATION_REJECTED",
                "Your application for '" + application.getJob().getTitle() + "' was not selected this time.");
        log.info("Application {} rejected for job {} by {}", id, application.getJob().getId(), principal.uid());
        return toResponse(saved);
    }

    /** The worker withdraws their own pending application. */
    @Transactional
    public ApplicationResponse cancel(Long id, AuthPrincipal principal) {
        JobApplication application = findApplication(id);
        AuthPrincipal current = requirePrincipal(principal);
        if (!current.isAdmin() && !current.owns(application.getWorkerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own application");
        }
        if (!APPLIED.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a pending application can be cancelled (current status: " + application.getStatus() + ")");
        }
        application.setStatus(CANCELLED);
        application.setUpdatedAt(LocalDateTime.now());
        JobApplication saved = applications.save(application);
        notifications.notifyAuto(application.getJob().getEmployerEmail(), "APPLICATION_CANCELLED",
                (saved.getWorkerName() == null ? "A worker" : saved.getWorkerName())
                        + " withdrew their application for '" + application.getJob().getTitle() + "'.");
        return toResponse(saved);
    }

    /** Employer marks an accepted assignment as finished. */
    @Transactional
    public ApplicationResponse complete(Long id, AuthPrincipal principal) {
        JobApplication application = findApplication(id);
        requireJobOwner(application.getJob(), principal);
        if (!ACCEPTED.equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an accepted application can be completed (current status: " + application.getStatus() + ")");
        }
        application.setStatus(COMPLETED);
        application.setUpdatedAt(LocalDateTime.now());
        JobApplication saved = applications.save(application);

        Job job = application.getJob();
        boolean anyStillRunning = applications.countByJobIdAndStatus(job.getId(), ACCEPTED) > 0;
        if (!anyStillRunning && job.getSlotsRemaining() != null && job.getSlotsRemaining() == 0
                && !JobLifecycle.isTerminal(job.getStatus())) {
            job.setStatus(JobLifecycle.COMPLETED);
            jobs.save(job);
        }
        notifications.notifyAuto(saved.getWorkerEmail(), "ASSIGNMENT_COMPLETED",
                "Your work on '" + job.getTitle() + "' was marked as completed. Thank you!");
        log.info("Application {} completed for job {}", id, job.getId());
        return toResponse(saved);
    }

    /** Real worker dashboard counters. */
    @Transactional
    public WorkerSummaryResponse workerSummary(AuthPrincipal principal) {
        Long workerId = requireWorker(principal);
        jobService.expireStaleJobs();
        return new WorkerSummaryResponse(
                workerId,
                jobs.countByStatus(JobLifecycle.OPEN),
                applications.countByWorkerId(workerId),
                applications.countByWorkerIdAndStatus(workerId, APPLIED),
                applications.countByWorkerIdAndStatus(workerId, ACCEPTED),
                applications.countByWorkerIdAndStatus(workerId, REJECTED),
                applications.countByWorkerIdAndStatus(workerId, CANCELLED),
                applications.countByWorkerIdAndStatus(workerId, COMPLETED));
    }

    /** Real employer dashboard counters. */
    @Transactional
    public EmployerSummaryResponse employerSummary(AuthPrincipal principal) {
        AuthPrincipal current = requirePrincipal(principal);
        if (!current.hasRole("EMPLOYER", "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an employer can view this dashboard");
        }
        Long employerId = current.uid();
        jobService.expireStaleJobs();
        return new EmployerSummaryResponse(
                employerId,
                jobs.countByEmployerId(employerId),
                jobs.countByEmployerIdAndStatus(employerId, JobLifecycle.OPEN),
                jobs.countByEmployerIdAndStatus(employerId, JobLifecycle.ASSIGNED),
                jobs.countByEmployerIdAndStatus(employerId, JobLifecycle.IN_PROGRESS),
                jobs.countByEmployerIdAndStatus(employerId, JobLifecycle.COMPLETED),
                jobs.countByEmployerIdAndStatus(employerId, JobLifecycle.CANCELLED),
                jobs.countByEmployerIdAndStatus(employerId, JobLifecycle.FLAGGED),
                applications.countByEmployerId(employerId),
                applications.countByEmployerIdAndStatus(employerId, APPLIED),
                applications.countByEmployerIdAndStatus(employerId, ACCEPTED),
                applications.countByEmployerIdAndStatus(employerId, REJECTED),
                applications.countByEmployerIdAndStatus(employerId, CANCELLED),
                applications.countByEmployerIdAndStatus(employerId, COMPLETED));
    }

    /** Platform-wide application counters for the admin dashboard. */
    @Transactional(readOnly = true)
    public long countByStatus(String status) {
        return applications.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return applications.count();
    }

    // --- guards ---------------------------------------------------------------------------------

    private JobApplication findApplication(Long id) {
        return applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private AuthPrincipal requirePrincipal(AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal;
    }

    private Long requireWorker(AuthPrincipal principal) {
        AuthPrincipal current = requirePrincipal(principal);
        if (!current.hasRole("WORKER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a worker can perform this action");
        }
        return current.uid();
    }

    /** Only the employer who owns the job (or an admin) may see or decide on its applications. */
    private void requireJobOwner(Job job, AuthPrincipal principal) {
        AuthPrincipal current = requirePrincipal(principal);
        if (current.isAdmin()) {
            return;
        }
        if (!current.hasRole("EMPLOYER") || job.getEmployerId() == null || !current.owns(job.getEmployerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only manage applications for jobs you posted");
        }
    }

    private ApplicationResponse toResponse(JobApplication application) {
        Job job = application.getJob();
        return new ApplicationResponse(application.getId(), job.getId(), job.getTitle(), job.getEmployer(),
                job.getEmployerId(), job.getCategory(), job.getDistrict(), job.getCity(), job.getPayPerWorker(),
                job.getJobDate(), JobLifecycle.normalize(job.getStatus()), application.getWorkerId(),
                application.getWorkerName(), application.getWorkerEmail(), application.getWorkerSkills(),
                application.getMessage(), application.getStatus(), application.getAppliedAt(),
                application.getUpdatedAt());
    }
}
