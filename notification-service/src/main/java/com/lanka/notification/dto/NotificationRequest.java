package com.lanka.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Notification to record. Sent by user-service, job-service and broker-service over the internal
 * network with the shared {@code X-Internal-Token} header.
 */
public record NotificationRequest(
        @NotBlank(message = "recipient is required")
        @Size(max = 160) String recipient,

        @Pattern(regexp = "EMAIL|SMS", message = "channel must be EMAIL or SMS")
        String channel,

        @Size(max = 60) String type,

        @Size(max = 160) String subject,

        @NotBlank(message = "message is required")
        @Size(max = 1000) String message) {
}
