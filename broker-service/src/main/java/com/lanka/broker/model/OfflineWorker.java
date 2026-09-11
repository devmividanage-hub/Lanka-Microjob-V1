package com.lanka.broker.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A worker managed by a broker who cannot use the platform directly (no smartphone / no account).
 *
 * <p>The worker is district-locked to the broker's own district: the value is copied from the
 * broker on the server, so a client cannot register workers outside the broker's assigned area.</p>
 */
@Entity
@Table(name = "offline_worker",
        indexes = {
                @Index(name = "idx_offline_worker_broker", columnList = "broker_entity_id"),
                @Index(name = "idx_offline_worker_status", columnList = "status"),
                @Index(name = "idx_offline_worker_district", columnList = "district")
        })
public class OfflineWorker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public broker reference (BRK-0001) kept for readability in exports. */
    @Column(name = "broker_id", length = 20)
    private String brokerId;

    /** Owning broker's primary key - the value used for every ownership check. */
    @Column(name = "broker_entity_id", nullable = false)
    private Long brokerEntityId;

    @Column(nullable = false, length = 120)
    private String workerName;

    @Column(length = 20)
    private String workerNic;

    @Column(length = 20)
    private String mobile;

    @Column(length = 80)
    private String district;

    @Column(length = 80)
    private String city;

    @Column(length = 400)
    private String skills;

    @Column(length = 40)
    private String availability;

    /** ACTIVE, ON_JOB or INACTIVE. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    private Integer totalJobs = 0;

    private Double rating;

    private Long commissionEarned = 0L;

    private LocalDateTime registeredAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "ACTIVE";
        if (totalJobs == null) totalJobs = 0;
        if (commissionEarned == null) commissionEarned = 0L;
        if (registeredAt == null) registeredAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBrokerId() {
        return brokerId;
    }

    public void setBrokerId(String brokerId) {
        this.brokerId = brokerId;
    }

    public Long getBrokerEntityId() {
        return brokerEntityId;
    }

    public void setBrokerEntityId(Long brokerEntityId) {
        this.brokerEntityId = brokerEntityId;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getWorkerNic() {
        return workerNic;
    }

    public void setWorkerNic(String workerNic) {
        this.workerNic = workerNic;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
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

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalJobs() {
        return totalJobs;
    }

    public void setTotalJobs(Integer totalJobs) {
        this.totalJobs = totalJobs;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Long getCommissionEarned() {
        return commissionEarned;
    }

    public void setCommissionEarned(Long commissionEarned) {
        this.commissionEarned = commissionEarned;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
}
