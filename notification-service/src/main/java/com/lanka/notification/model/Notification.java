package com.lanka.notification.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A stored notification.
 *
 * <p>{@code status} is SIMULATED for every notification produced by this project because only mock
 * providers are wired in. It becomes SENT only when a real provider confirms delivery, and FAILED
 * when a provider reports an error - the value is therefore an honest record, never a claim.</p>
 */
@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notification_recipient", columnList = "recipient"),
                @Index(name = "idx_notification_type", columnList = "type"),
                @Index(name = "idx_notification_created_at", columnList = "created_at")
        })
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String recipient;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 160)
    private String subject;

    /** SIMULATED, SENT or FAILED. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 80)
    private String provider;

    @Column(length = 240)
    private String detail;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @PrePersist
    void prePersist() {
        if (channel == null) channel = "EMAIL";
        if (type == null) type = "GENERAL";
        if (status == null) status = "SIMULATED";
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
