package com.lanka.user.dto;

/** Profile of the authenticated caller, returned by {@code GET /auth/me}. */
public record UserProfileResponse(Long id, String name, String email, String mobile, String role, String district,
                                  String city, String nic, String skills, String availability, String status) {
}
