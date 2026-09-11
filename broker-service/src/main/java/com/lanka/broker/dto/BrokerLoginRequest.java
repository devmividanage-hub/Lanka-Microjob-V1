package com.lanka.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrokerLoginRequest(
        @Email(message = "Email must be a valid address")
        @Size(max = 160) String email,

        @Size(max = 20) String phone,

        @NotBlank(message = "Password is required")
        @Size(max = 72) String password) {

    @JsonIgnore
    @AssertTrue(message = "Either email or mobile number must be provided")
    public boolean isIdentifierProvided() {
        return (email != null && !email.isBlank()) || (phone != null && !phone.isBlank());
    }
}
