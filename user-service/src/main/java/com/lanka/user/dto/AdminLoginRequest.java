package com.lanka.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "Admin email is required")
        @Email(message = "Admin email must be a valid address")
        @Size(max = 160)
        String gmail,

        @NotBlank(message = "Password is required")
        @Size(max = 72)
        String password) {
}
