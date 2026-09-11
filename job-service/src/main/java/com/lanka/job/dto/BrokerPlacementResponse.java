package com.lanka.job.dto;

import java.time.LocalDateTime;

/** Persisted placement joined to its real job. */
public record BrokerPlacementResponse(Long placementId, Long brokerEntityId, String brokerId, Long workerId,
                                      String workerName, Long jobId, String jobTitle, Long employerId,
                                      String employer, String category, String district, String city,
                                      Integer payPerDay, long commissionAmount, LocalDateTime placementDate,
                                      String status, Integer jobSlotsRemaining, String jobStatus,
                                      Integer rating, LocalDateTime ratedAt) {
}
