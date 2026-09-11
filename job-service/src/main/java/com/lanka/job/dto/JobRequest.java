package com.lanka.job.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Job creation payload.
 *
 * <p>The employer identity is deliberately absent: it is taken from the authenticated JWT so a
 * caller can never post a job on somebody else's behalf.</p>
 */
public record JobRequest(
        @NotBlank(message = "Job title is required")
        @Size(max = 160, message = "Title must be at most 160 characters")
        String title,

        @NotBlank(message = "Category is required")
        @Size(max = 60)
        String category,

        @NotBlank(message = "District is required")
        @Size(max = 80)
        String district,

        @Size(max = 80)
        String city,

        @NotNull(message = "Workers needed is required")
        @Min(value = 1, message = "At least 1 worker is needed")
        @Max(value = 500, message = "Workers needed must be at most 500")
        Integer workersNeeded,

        @NotNull(message = "Pay per worker is required")
        @Min(value = 0, message = "Pay cannot be negative")
        @Max(value = 10_000_000, message = "Pay is unrealistically high")
        Integer payPerWorker,

        @NotNull(message = "Job date is required")
        LocalDate jobDate,

        Boolean urgent,

        @Size(max = 400, message = "Required skills list is too long")
        String requiredSkills,

        @Size(max = 1000, message = "Additional notes must be at most 1000 characters")
        String additionalNotes) {
}
