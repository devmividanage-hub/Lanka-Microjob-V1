package com.lanka.user.dto;

/**
 * Successful login. The {@code token} is a signed JWT carrying {@code sub} (email or mobile),
 * {@code role}, {@code uid} (database id) and {@code name}; downstream services use {@code uid}
 * for ownership checks instead of trusting ids supplied by the browser.
 */
public record LoginResponse(String token, String role, String name, Long id, String status, String district,
                            String city, String email, String mobile, String nic, String skills,
                            String availability) {
}
