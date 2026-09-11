package com.lanka.job.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A worker's application. The worker identity always comes from the JWT, never from the body. */
public record ApplicationRequest(
        @NotNull(message = "jobId is required")
        Long jobId,

        @Size(max = 500, message = "Message must be at most 500 characters")
        String message) {
}
