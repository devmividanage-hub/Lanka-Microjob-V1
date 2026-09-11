package com.lanka.job.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An offline-worker assignment created by a broker. The Job association is a real database foreign
 * key; broker and worker ids remain cross-service references because their lifecycle belongs to
 * broker-service.
 */
@Entity
@Table(name = "broker_placement",
        uniqueConstraints = @UniqueConstraint(name = "uk_broker_placement_worker_job",
                columnNames = {"offline_worker_id", "job_id"}),
        indexes = {
                @Index(name = "idx_broker_placement_broker", columnList = "broker_entity_id"),
                @Index(name = "idx_broker_placement_worker", columnList = "offline_worker_id"),
                @Index(name = "idx_broker_placement_job", columnList = "job_id")
        })
public class BrokerPlacement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_entity_id", nullable = false)
    private Long brokerEntityId;

    @Column(name = "broker_id", nullable = false, length = 20)
    private String brokerId;

    @Column(name = "offline_worker_id", nullable = false)
    private Long offlineWorkerId;

    @Column(nullable = false, length = 120)
    private String workerName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_broker_placement_job"))
    private Job job;

    private Long employerId;

    @Column(length = 160)
    private String employer;

    private Integer payPerDay;

    private Long commissionAmount;

    /** Rating supplied by the authenticated employer who owns the linked job. */
    private Integer rating;

    private LocalDateTime ratedAt;

    @Column(nullable = false, length = 20)
    private String status = "PLACED";

    private LocalDateTime placementDate;

    @PrePersist
    void prePersist() {
        if (status == null) status = "PLACED";
        if (placementDate == null) placementDate = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getBrokerEntityId() { return brokerEntityId; }
    public void setBrokerEntityId(Long brokerEntityId) { this.brokerEntityId = brokerEntityId; }
    public String getBrokerId() { return brokerId; }
    public void setBrokerId(String brokerId) { this.brokerId = brokerId; }
    public Long getOfflineWorkerId() { return offlineWorkerId; }
    public void setOfflineWorkerId(Long offlineWorkerId) { this.offlineWorkerId = offlineWorkerId; }
    public String getWorkerName() { return workerName; }
    public void setWorkerName(String workerName) { this.workerName = workerName; }
    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }
    public Long getEmployerId() { return employerId; }
    public void setEmployerId(Long employerId) { this.employerId = employerId; }
    public String getEmployer() { return employer; }
    public void setEmployer(String employer) { this.employer = employer; }
    public Integer getPayPerDay() { return payPerDay; }
    public void setPayPerDay(Integer payPerDay) { this.payPerDay = payPerDay; }
    public Long getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(Long commissionAmount) { this.commissionAmount = commissionAmount; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public LocalDateTime getRatedAt() { return ratedAt; }
    public void setRatedAt(LocalDateTime ratedAt) { this.ratedAt = ratedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPlacementDate() { return placementDate; }
}
