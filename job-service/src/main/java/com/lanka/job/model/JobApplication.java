package com.lanka.job.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A worker's application for a job - the persistent replacement for the old UI-only
 * "accept job" behaviour.
 *
 * <p>Uniqueness of {@code (job_id, worker_id)} is enforced in the database, so a worker can never
 * apply twice for the same job even under concurrent requests.</p>
 *
 * <p>{@code workerId} and {@code employerId} reference rows owned by the user-service. They share
 * this PostgreSQL instance (single database, per the project's documented architecture decision)
 * but are intentionally kept as indexed plain columns rather than cross-service foreign keys.</p>
 */
@Entity
@Table(name = "job_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_job_worker", columnNames = {"job_id", "worker_id"}),
        indexes = {
                @Index(name = "idx_application_worker", columnList = "worker_id"),
                @Index(name = "idx_application_employer", columnList = "employer_id"),
                @Index(name = "idx_application_status", columnList = "status"),
                @Index(name = "idx_application_job_status", columnList = "job_id,status")
        })
public class JobApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logical foreign key to {@link Job#getId()} (same service, same database). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_application_job"))
    private Job job;

    @Column(name = "worker_id", nullable = false)
    private Long workerId;

    @Column(name = "employer_id", nullable = false)
    private Long employerId;

    @Column(length = 120)
    private String workerName;

    @Column(length = 160)
    private String workerEmail;

    @Column(length = 400)
    private String workerSkills;

    @Column(length = 500)
    private String message;

    @Column(nullable = false, length = 20)
    private String status = "APPLIED";

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "APPLIED";
        if (appliedAt == null) appliedAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = appliedAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }

    public Long getEmployerId() {
        return employerId;
    }

    public void setEmployerId(Long employerId) {
        this.employerId = employerId;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getWorkerEmail() {
        return workerEmail;
    }

    public void setWorkerEmail(String workerEmail) {
        this.workerEmail = workerEmail;
    }

    public String getWorkerSkills() {
        return workerSkills;
    }

    public void setWorkerSkills(String workerSkills) {
        this.workerSkills = workerSkills;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
