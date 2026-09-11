package com.lanka.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Stored notification.
 *
 * @param status   SIMULATED while only mock providers are configured (see application.properties)
 * @param provider which provider implementation handled it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(Long id, String recipient, String channel, String type, String subject,
                                   String message, String status, String provider, String detail,
                                   LocalDateTime createdAt, LocalDateTime sentAt) {
}
