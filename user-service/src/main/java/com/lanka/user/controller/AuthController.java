package com.lanka.user.controller;

import com.lanka.user.dto.*;
import com.lanka.user.security.AuthPrincipal;
import com.lanka.user.security.SecurityUtils;
import com.lanka.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication & Users", description = "Registration, login, admin approval and user directory")
public class AuthController {
    private final UserService service;

    public AuthController(UserService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a WORKER or EMPLOYER account (starts in PENDING state, public)")
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login a WORKER or EMPLOYER; only APPROVED accounts receive a JWT (public)")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/admin/login")
    @Operation(summary = "Login the platform administrator (public)")
    AdminLoginResponse adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        return service.adminLogin(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Profile of the authenticated caller (any role)")
    UserProfileResponse me() {
        return service.me(SecurityUtils.require());
    }

    @GetMapping("/users/pending")
    @Operation(summary = "Users waiting for approval (ADMIN only)")
    List<UserApprovalResponse> pendingUsers() {
        return service.getPendingUsers();
    }

    @GetMapping("/users")
    @Operation(summary = "User directory, optionally filtered by role/status (ADMIN only)")
    List<UserApprovalResponse> listUsers(@RequestParam(required = false) String role,
                                         @RequestParam(required = false) String status) {
        return service.listUsers(role, status);
    }

    @GetMapping("/stats")
    @Operation(summary = "Real user counters for the admin dashboard (ADMIN only)")
    AdminStatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/public-stats")
    @Operation(summary = "Approved worker count for the public landing page")
    Map<String, Long> publicStats() {
        return service.publicStats();
    }

    @PutMapping("/users/{id}/approve")
    @Operation(summary = "Approve a pending user (ADMIN only)")
    UserApprovalResponse approveUser(@PathVariable Long id) {
        requireAdmin();
        return service.approveUser(id);
    }

    @PutMapping("/users/{id}/reject")
    @Operation(summary = "Reject a pending user (ADMIN only)")
    UserApprovalResponse rejectUser(@PathVariable Long id) {
        requireAdmin();
        return service.rejectUser(id);
    }

    /**
     * Second, explicit role check. The filter chain already restricts /auth/users/** to ADMIN;
     * this keeps the rule visible on the endpoints that mutate data.
     */
    private void requireAdmin() {
        AuthPrincipal principal = SecurityUtils.require();
        if (!principal.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an administrator can approve or reject users");
        }
    }
}
