package com.lanka.broker.dto;

import java.time.LocalDateTime;

/** Full database-backed placement details returned by job-service. */
public record PlacementRecordResponse(Long placementId, Long brokerEntityId, String brokerId, Long workerId,
                                      String workerName, Long jobId, String jobTitle, Long employerId,
                                      String employer, String category, String district, String city,
                                      Integer payPerDay, long commissionAmount, LocalDateTime placementDate,
                                      String status, Integer jobSlotsRemaining, String jobStatus,
                                      Integer rating, LocalDateTime ratedAt) {
}
