package com.lanka.broker.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A district labour broker. Brokers apply through the public form, an administrator approves or
 * rejects them, and only an APPROVED broker can log in and manage offline workers.
 *
 * <p>{@code commissionEarned} is real: it is the sum of the platform commission recorded for every
 * placement the broker logs against one of their offline workers.</p>
 */
@Entity
@Table(name = "broker",
        uniqueConstraints = @UniqueConstraint(name = "uk_broker_broker_id", columnNames = "broker_id"),
        indexes = {
                @Index(name = "idx_broker_status", columnList = "status"),
                @Index(name = "idx_broker_email", columnList = "email"),
                @Index(name = "idx_broker_phone", columnList = "phone"),
                @Index(name = "idx_broker_district", columnList = "district")
        })
public class Broker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String nic;

    @Column(length = 20)
    private String phone;

    @Column(length = 160)
    private String email;

    /** BCrypt hash - never returned to a client. */
    @Column(nullable = false)
    private String password;

    /** Public reference such as BRK-0001, assigned when the admin approves the application. */
    @Column(name = "broker_id", length = 20)
    private String brokerId;

    @Column(length = 80)
    private String district;

    @Column(length = 80)
    private String city;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(length = 40)
    private String yearsExperience;

    @Column(length = 40)
    private String estimatedWorkers;

    @Column(length = 1000)
    private String workerMethod;

    @Column(length = 120)
    private String idProof;

    private Integer totalWorkers = 0;

    private Long commissionEarned = 0L;

    private Long totalPlacements = 0L;

    private LocalDateTime submittedAt;

    private LocalDateTime reviewedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "PENDING";
        if (totalWorkers == null) totalWorkers = 0;
        if (commissionEarned == null) commissionEarned = 0L;
        if (totalPlacements == null) totalPlacements = 0L;
        if (submittedAt == null) submittedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBrokerId() {
        return brokerId;
    }

    public void setBrokerId(String brokerId) {
        this.brokerId = brokerId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getYearsExperience() {
        return yearsExperience;
    }

    public void setYearsExperience(String yearsExperience) {
        this.yearsExperience = yearsExperience;
    }

    public String getEstimatedWorkers() {
        return estimatedWorkers;
    }

    public void setEstimatedWorkers(String estimatedWorkers) {
        this.estimatedWorkers = estimatedWorkers;
    }

    public String getWorkerMethod() {
        return workerMethod;
    }

    public void setWorkerMethod(String workerMethod) {
        this.workerMethod = workerMethod;
    }

    public String getIdProof() {
        return idProof;
    }

    public void setIdProof(String idProof) {
        this.idProof = idProof;
    }

    public Integer getTotalWorkers() {
        return totalWorkers;
    }

    public void setTotalWorkers(Integer totalWorkers) {
        this.totalWorkers = totalWorkers;
    }

    public Long getCommissionEarned() {
        return commissionEarned;
    }

    public void setCommissionEarned(Long commissionEarned) {
        this.commissionEarned = commissionEarned;
    }

    public Long getTotalPlacements() {
        return totalPlacements;
    }

    public void setTotalPlacements(Long totalPlacements) {
        this.totalPlacements = totalPlacements;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
