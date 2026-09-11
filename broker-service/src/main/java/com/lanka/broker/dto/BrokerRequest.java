package com.lanka.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

/** Public broker application. Stays PENDING until an administrator reviews it. */
public record BrokerRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120) String name,

        @NotBlank(message = "NIC is required")
        @Pattern(regexp = "^[0-9]{9}[VvXx]$|^[0-9]{12}$", message = "NIC must be a valid Sri Lankan NIC")
        String nic,

        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Mobile must be a valid phone number")
        String phone,

        @Email(message = "Email must be a valid address")
        @Size(max = 160) String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotBlank(message = "District is required")
        @Size(max = 80) String district,

        @Size(max = 80) String city,

        @Size(max = 40) String yearsExperience,

        @Size(max = 40) String estimatedWorkers,

        @Size(max = 1000) String workerMethod,

        @Size(max = 120) String idProof,

        Boolean agreedToCodeOfConduct) {

    @JsonIgnore
    @AssertTrue(message = "Either email or mobile must be provided")
    public boolean isContactProvided() {
        return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
    }

    @JsonIgnore
    @AssertTrue(message = "You must accept the broker code of conduct")
    public boolean isAgreementAccepted() {
        return Boolean.TRUE.equals(agreedToCodeOfConduct);
    }
}
