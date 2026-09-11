package com.lanka.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Trusted service-to-service worker snapshot used to validate an offline-worker placement.
 * Broker-service builds this only after proving that the authenticated broker owns the worker.
 */
public record OfflineWorkerJobRequest(
        @NotNull Long brokerEntityId,
        @NotBlank @Size(max = 20) String brokerId,
        @NotNull Long workerId,
        @NotBlank @Size(max = 120) String workerName,
        @NotBlank @Size(max = 80) String district,
        @Size(max = 80) String city,
        @Size(max = 400) String skills) {
}
