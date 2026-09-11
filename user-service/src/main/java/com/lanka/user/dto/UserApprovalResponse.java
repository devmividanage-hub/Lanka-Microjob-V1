package com.lanka.user.dto;

import java.time.LocalDateTime;

/** User row as shown in the admin approval queue and the admin user directory. */
public record UserApprovalResponse(Long id, String name, String email, String mobile, String role, String district,
                                   String city, String nic, String skills, String availability, String status,
                                   LocalDateTime registeredAt) {
}
