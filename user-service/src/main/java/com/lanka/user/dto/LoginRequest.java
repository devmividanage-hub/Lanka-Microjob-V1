package com.lanka.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Login by email (preferred) or mobile, scoped to the role the user selected in the UI. */
public record LoginRequest(
        @Pattern(regexp = "^$|^[0-9+\\-\\s]{7,20}$", message = "Mobile must be a valid phone number")
        String mobile,

        @Email(message = "Email must be a valid address")
        @Size(max = 160) String email,

        @NotBlank(message = "Password is required")
        @Size(max = 72) String password,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "ADMIN|EMPLOYER|WORKER|BROKER", message = "Unsupported role")
        String role) {

    @JsonIgnore
    @AssertTrue(message = "Either email or mobile must be provided")
    public boolean isIdentifierProvided() {
        return (email != null && !email.isBlank()) || (mobile != null && !mobile.isBlank());
    }
}
