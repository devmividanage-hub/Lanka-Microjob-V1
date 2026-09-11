package com.lanka.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Worker / employer self registration.
 *
 * <p>Security note: the role is restricted to WORKER and EMPLOYER on purpose. ADMIN accounts are
 * provisioned by {@code AdminSeeder} and BROKER accounts go through the broker-service approval
 * workflow, so a client can never escalate its own role.</p>
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name must be at most 120 characters")
        String name,

        @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Mobile must be a valid phone number")
        String mobile,

        @Email(message = "Email must be a valid address")
        @Size(max = 160, message = "Email must be at most 160 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "WORKER|EMPLOYER", message = "Role must be WORKER or EMPLOYER")
        String role,

        @Size(max = 80) String district,
        @Size(max = 80) String city,

        @Pattern(regexp = "^$|^[0-9]{9}[VvXx]$|^[0-9]{12}$", message = "NIC must be a valid Sri Lankan NIC")
        String nic,

        @Size(max = 400, message = "Skills list is too long") String skills,

        @Size(max = 40) String availability) {

    @JsonIgnore
    @AssertTrue(message = "Either email or mobile must be provided")
    public boolean isIdentifierProvided() {
        return notBlank(email) || notBlank(mobile);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
