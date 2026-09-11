package com.lanka.job.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A micro job posted by an employer.
 *
 * <p>{@code employerId} is the id of the {@code users} row that created the job. It is taken from
 * the JWT on creation and is the basis of every ownership check - the browser never supplies it.</p>
 *
 * <p>Lifecycle: OPEN -> ASSIGNED (all slots filled) -> IN_PROGRESS -> COMPLETED, with CANCELLED,
 * EXPIRED and FLAGGED as side states. See {@link com.lanka.job.service.JobLifecycle}.</p>
 */
@Entity
@Table(name = "job",
        indexes = {
                @Index(name = "idx_job_status", columnList = "status"),
                @Index(name = "idx_job_employer", columnList = "employer_id"),
                @Index(name = "idx_job_district_status", columnList = "district,status"),
                @Index(name = "idx_job_city_status", columnList = "city,status"),
                @Index(name = "idx_job_category_status", columnList = "category,status"),
                @Index(name = "idx_job_date", columnList = "job_date")
        })
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    /** Employer display name captured from the authenticated account at creation time. */
    @Column(length = 160)
    private String employer;

    private Long employerId;

    @Column(length = 160)
    private String employerEmail;

    @Column(length = 60)
    private String category;

    @Column(length = 80)
    private String district;

    @Column(length = 80)
    private String city;

    private Integer workersNeeded;

    private Integer payPerWorker;

    private LocalDate jobDate;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    private Boolean urgent = false;

    private Integer slotsRemaining;

    @Column(length = 400)
    private String requiredSkills;

    @Column(length = 1000)
    private String additionalNotes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "OPEN";
        if (urgent == null) urgent = false;
        if (slotsRemaining == null) slotsRemaining = workersNeeded;
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEmployer() {
        return employer;
    }

    public void setEmployer(String employer) {
        this.employer = employer;
    }

    public Long getEmployerId() {
        return employerId;
    }

    public void setEmployerId(Long employerId) {
        this.employerId = employerId;
    }

    public String getEmployerEmail() {
        return employerEmail;
    }

    public void setEmployerEmail(String employerEmail) {
        this.employerEmail = employerEmail;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Integer getWorkersNeeded() {
        return workersNeeded;
    }

    public void setWorkersNeeded(Integer workersNeeded) {
        this.workersNeeded = workersNeeded;
    }

    public Integer getPayPerWorker() {
        return payPerWorker;
    }

    public void setPayPerWorker(Integer payPerWorker) {
        this.payPerWorker = payPerWorker;
    }

    public LocalDate getJobDate() {
        return jobDate;
    }

    public void setJobDate(LocalDate jobDate) {
        this.jobDate = jobDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean urgent) {
        this.urgent = urgent;
    }

    public Integer getSlotsRemaining() {
        return slotsRemaining;
    }

    public void setSlotsRemaining(Integer slotsRemaining) {
        this.slotsRemaining = slotsRemaining;
    }

    public String getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(String requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public String getAdditionalNotes() {
        return additionalNotes;
    }

    public void setAdditionalNotes(String additionalNotes) {
        this.additionalNotes = additionalNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
