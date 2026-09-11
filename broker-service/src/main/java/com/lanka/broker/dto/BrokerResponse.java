package com.lanka.broker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/** Broker as shown in the admin review queue. The password hash is never included. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrokerResponse(Long id, String brokerId, String name, String nic, String email, String phone,
                             String district, String city, String status, String yearsExperience,
                             String estimatedWorkers, String workerMethod, String idProof, Integer totalWorkers,
                             Long commissionEarned, Long totalPlacements, LocalDateTime submittedAt,
                             LocalDateTime reviewedAt) {
}
