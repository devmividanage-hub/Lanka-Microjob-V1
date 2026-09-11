package com.lanka.user.model;

import jakarta.persistence.*;

/**
 * A platform user (WORKER or EMPLOYER). Administrators live in {@link Admin}.
 *
 * <p>{@code skills} is stored as a comma separated string because the worker skill list is a
 * short, closed vocabulary taken from the UI skill chips. It is read by the matching-service
 * through the login response, so no join table is needed for this project.</p>
 */
@Entity
@Table(name = "users",
        indexes = {
                @Index(name = "idx_users_status", columnList = "status"),
                @Index(name = "idx_users_role_status", columnList = "role,status"),
                @Index(name = "idx_users_district", columnList = "district")
        })
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(unique = true, length = 20)
    private String mobile;

    @Column(unique = true, length = 160)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(length = 80)
    private String district;

    @Column(length = 80)
    private String city;

    @Column(length = 30)
    private String nic;

    @Column(length = 400)
    private String skills;

    @Column(length = 40)
    private String availability;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    private java.time.LocalDateTime registeredAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "PENDING";
        if (registeredAt == null) registeredAt = java.time.LocalDateTime.now();
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

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public String getNic() {
        return nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
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

    public java.time.LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(java.time.LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    /** Convenience accessor used when notifying the user (email preferred, mobile as fallback). */
    @Transient
    public String getContact() {
        return email != null && !email.isBlank() ? email : mobile;
    }
}
