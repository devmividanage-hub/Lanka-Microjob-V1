package com.lanka.broker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Offline worker row. {@code rating} stays null until a rating exists, so the UI can honestly show
 * "not rated yet" instead of an invented number.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfflineWorkerResponse(Long id, String brokerId, String workerName, String workerNic, String mobile,
                                    String district, String city, String skills, String availability, String status,
                                    Integer totalJobs, Double rating, Long commissionEarned,
                                    LocalDateTime registeredAt) {
}
