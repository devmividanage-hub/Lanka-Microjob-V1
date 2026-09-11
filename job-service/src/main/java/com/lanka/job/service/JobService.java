package com.lanka.job.service;

import com.lanka.job.client.NotificationClient;
import com.lanka.job.dto.JobRequest;
import com.lanka.job.dto.JobResponse;
import com.lanka.job.model.Job;
import com.lanka.job.repository.JobApplicationRepository;
import com.lanka.job.repository.JobRepository;
import com.lanka.job.repository.JobSpecifications;
import com.lanka.job.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final NotificationClient notifications;

    public JobService(JobRepository jobs, JobApplicationRepository applications, NotificationClient notifications) {
        this.jobs = jobs;
        this.applications = applications;
        this.notifications = notifications;
    }

    /**
     * Creates a job for the authenticated employer. The employer id/name/email come from the JWT,
     * never from the request body.
     */
    @Transactional
    public JobResponse create(JobRequest request, AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!principal.hasRole("EMPLOYER", "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an employer can post a job");
        }
        if (request.jobDate() != null && request.jobDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job date cannot be in the past");
        }
        String employerEmail = principal.identifier() != null && principal.identifier().contains("@")
                ? principal.identifier() : null;
        Job saved = persistNewJob(request, principal.uid(), principal.name(), employerEmail);
        notifications.notifyAuto(employerEmail, "JOB_POSTED",
                "Your job '" + saved.getTitle() + "' is live on the Lanka MicroJob worker feed.");
        return toResponse(saved, null);
    }

    /**
     * Internal creation path shared by {@link #create} and the demo seeder, so seeded rows have the
     * exact same shape as real ones.
     */
    @Transactional
    public Job persistNewJob(JobRequest request, Long employerId, String employerName, String employerEmail) {
        Job job = new Job();
        job.setTitle(request.title().trim());
        job.setEmployer(employerName == null || employerName.isBlank() ? "Lanka MicroJob Employer" : employerName.trim());
        job.setEmployerId(employerId);
        job.setEmployerEmail(employerEmail);
        job.setCategory(normalizeText(request.category()));
        job.setDistrict(normalizeText(request.district()));
        job.setCity(normalizeText(request.city()));
        job.setWorkersNeeded(request.workersNeeded());
        job.setSlotsRemaining(request.workersNeeded());
        job.setPayPerWorker(request.payPerWorker());
        job.setJobDate(request.jobDate());
        job.setStatus(JobLifecycle.OPEN);
        job.setUrgent(Boolean.TRUE.equals(request.urgent()));
        job.setRequiredSkills(normalizeSkills(request.requiredSkills()));
        job.setAdditionalNotes(normalizeText(request.additionalNotes()));
        return jobs.save(job);
    }

    /**
     * Public job feed. Stale jobs are expired first so a job whose working day has passed is never
     * offered again, and is not counted as available.
     */
    @Transactional
    public List<JobResponse> search(String district, String city, String category, String search, boolean includeClosed) {
        expireStaleJobs();
        LocalDate today = LocalDate.now();
        Specification<Job> spec = Specification
                .where(JobSpecifications.district(district))
                .and(JobSpecifications.city(city))
                .and(JobSpecifications.category(category))
                .and(JobSpecifications.search(search))
                .and(JobSpecifications.notExpired(today));
        if (!includeClosed) {
            spec = spec.and(JobSpecifications.statusIn(List.of(JobLifecycle.OPEN)))
                    .and(JobSpecifications.hasFreeSlots());
        }
        return jobs.findAll(spec).stream().map(job -> toResponse(job, null)).toList();
    }

    /** Jobs owned by the caller (employer dashboard / "My Jobs"). */
    @Transactional
    public List<JobResponse> myJobs(AuthPrincipal principal, String status) {
        Long employerId = requireEmployer(principal);
        expireStaleJobs();
        Specification<Job> spec = Specification.where(JobSpecifications.employerId(employerId));
        if (status != null && !status.isBlank()) {
            spec = spec.and(JobSpecifications.statusIn(List.of(JobLifecycle.normalize(status))));
        }
        return jobs.findAll(spec).stream()
                .map(job -> toResponse(job, (int) applications.countByJobId(job.getId())))
                .toList();
    }

    /** Jobs owned by a specific employer. Callers must be that employer or an admin. */
    @Transactional
    public List<JobResponse> jobsOfEmployer(Long employerId, AuthPrincipal principal) {
        requireEmployerAccess(employerId, principal);
        expireStaleJobs();
        return jobs.findByEmployerIdOrderByIdDesc(employerId).stream()
                .map(job -> toResponse(job, (int) applications.countByJobId(job.getId())))
                .toList();
    }

    @Transactional
    public JobResponse getById(Long id) {
        Job job = findEntity(id);
        expireIfStale(job);
        return toResponse(job, (int) applications.countByJobId(job.getId()));
    }

    /** Platform-wide job counters, used by the worker and admin dashboards. */
    @Transactional
    public Map<String, Object> stats() {
        expireStaleJobs();
        return Map.of(
                "totalJobs", jobs.count(),
                "openJobs", jobs.countByStatus(JobLifecycle.OPEN),
                "assignedJobs", jobs.countByStatus(JobLifecycle.ASSIGNED),
                "inProgressJobs", jobs.countByStatus(JobLifecycle.IN_PROGRESS),
                "completedJobs", jobs.countByStatus(JobLifecycle.COMPLETED),
                "cancelledJobs", jobs.countByStatus(JobLifecycle.CANCELLED),
                "expiredJobs", jobs.countByStatus(JobLifecycle.EXPIRED),
                "flaggedJobs", jobs.countByStatus(JobLifecycle.FLAGGED),
                "totalApplications", applications.count(),
                "pendingApplications", applications.countByStatus("APPLIED"));
    }

    /** Non-sensitive counter used by the public landing page. */
    @Transactional
    public Map<String, Long> publicStats() {
        expireStaleJobs();
        return Map.of("totalJobs", jobs.count());
    }

    /** All jobs, including closed and expired rows, for the KPI-consistent admin detail view. */
    @Transactional
    public List<JobResponse> listForAdmin(AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can list all jobs");
        }
        expireStaleJobs();
        return jobs.findAllByOrderByIdDesc().stream()
                .map(job -> toResponse(job, (int) applications.countByJobId(job.getId())))
                .toList();
    }

    /**
     * Employer/admin status change, guarded by the lifecycle in {@link JobLifecycle}.
     * Cancelling also cancels the applications that are still pending.
     */
    @Transactional
    public JobResponse updateStatus(Long id, String status, AuthPrincipal principal) {
        Job job = findEntity(id);
        expireIfStale(job);
        String target = JobLifecycle.normalize(status);
        if (!JobLifecycle.isKnown(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported job status. Allowed: " + String.join(", ", JobLifecycle.ALL));
        }
        if (JobLifecycle.FLAGGED.equals(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use PUT /jobs/{id}/flag to flag a job");
        }
        requireJobAccess(job, principal);
        if (!JobLifecycle.canTransition(job.getStatus(), target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot change job status from " + JobLifecycle.normalize(job.getStatus()) + " to " + target);
        }
        job.setStatus(target);
        if (JobLifecycle.CANCELLED.equals(target)) {
            int cancelled = applications.cancelPendingForJob(job.getId(), LocalDateTime.now());
            log.info("Job {} cancelled by {}: {} pending application(s) cancelled", id, principal.uid(), cancelled);
        }
        Job saved = jobs.save(job);
        notifications.notifyAuto(saved.getEmployerEmail(), "JOB_STATUS_CHANGED",
                "Job '" + saved.getTitle() + "' is now " + target.replace('_', ' ') + ".");
        return toResponse(saved, (int) applications.countByJobId(saved.getId()));
    }

    /** Admin moderation: hides a job from the feed until it is reviewed. */
    @Transactional
    public JobResponse flagJob(Long id, AuthPrincipal principal) {
        if (principal == null || !principal.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can flag a job");
        }
        Job job = findEntity(id);
        job.setStatus(JobLifecycle.FLAGGED);
        Job saved = jobs.save(job);
        log.info("Job {} flagged by admin {}", id, principal.uid());
        return toResponse(saved, (int) applications.countByJobId(saved.getId()));
    }

    // --- helpers shared with ApplicationService -------------------------------------------------

    @Transactional
    public Job findEntity(Long id) {
        return jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    public JobResponse toResponse(Job job, Integer applicationCount) {
        return new JobResponse(job.getId(), job.getTitle(), job.getEmployer(), job.getEmployerId(), job.getCategory(),
                job.getDistrict(), job.getCity(), job.getWorkersNeeded(), job.getPayPerWorker(), job.getJobDate(),
                JobLifecycle.normalize(job.getStatus()), job.getUrgent(), job.getSlotsRemaining(),
                job.getRequiredSkills(), job.getAdditionalNotes(), applicationCount, job.getCreatedAt(),
                job.getUpdatedAt());
    }

    /** Marks OPEN jobs whose working day has passed as EXPIRED and cancels their pending applications. */
    @Transactional
    public int expireStaleJobs() {
        List<Job> stale = jobs.findByStatusAndJobDateBefore(JobLifecycle.OPEN, LocalDate.now());
        if (stale.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Job job : stale) {
            job.setStatus(JobLifecycle.EXPIRED);
            jobs.save(job);
            applications.cancelPendingForJob(job.getId(), now);
        }
        log.info("Expired {} job(s) whose working day has passed", stale.size());
        return stale.size();
    }

    private void expireIfStale(Job job) {
        if (JobLifecycle.isOpen(job.getStatus()) && job.getJobDate() != null && job.getJobDate().isBefore(LocalDate.now())) {
            job.setStatus(JobLifecycle.EXPIRED);
            jobs.save(job);
            applications.cancelPendingForJob(job.getId(), LocalDateTime.now());
        }
    }

    private Long requireEmployer(AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!principal.hasRole("EMPLOYER", "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an employer can list their own jobs");
        }
        return principal.uid();
    }

    private void requireEmployerAccess(Long employerId, AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!principal.isAdmin() && !principal.owns(employerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own jobs");
        }
    }

    /** Owner (the employer who posted it) or an administrator. */
    private void requireJobAccess(Job job, AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (principal.isAdmin()) {
            return;
        }
        if (!principal.hasRole("EMPLOYER") || job.getEmployerId() == null || !principal.owns(job.getEmployerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage jobs you posted");
        }
    }

    private String normalizeSkills(String value) {
        String text = normalizeText(value);
        if (text == null) return null;
        List<String> skills = java.util.Arrays.stream(text.split(","))
                .map(String::trim).filter(skill -> !skill.isEmpty()).distinct().toList();
        return skills.isEmpty() ? null : String.join(",", skills);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
