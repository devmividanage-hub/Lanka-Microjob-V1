package com.lanka.broker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Offline worker registration.
 *
 * <p>District and city are intentionally absent: the worker is locked to the broker's own district
 * and city, which the service copies from the authenticated broker's record.</p>
 */
public record OfflineWorkerRequest(
        @NotBlank(message = "Worker name is required")
        @Size(max = 120, message = "Worker name must be at most 120 characters")
        String workerName,

        @Pattern(regexp = "^$|^[0-9]{9}[VvXx]$|^[0-9]{12}$", message = "NIC must be a valid Sri Lankan NIC")
        String workerNic,

        @NotBlank(message = "Mobile number is required so the broker can be reached about this worker")
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Mobile must be a valid phone number")
        String mobile,

        @NotBlank(message = "At least one skill is required")
        @Size(max = 400, message = "Skills list is too long")
        String skills,

        @Size(max = 40) String availability) {
}
