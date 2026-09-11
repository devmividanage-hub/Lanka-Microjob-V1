package com.lanka.broker.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The browser selects a real job only. Pay, employer and commission are read by job-service.
 */
public record PlacementRequest(
        @NotNull(message = "A real job is required") Long jobId) {
}
