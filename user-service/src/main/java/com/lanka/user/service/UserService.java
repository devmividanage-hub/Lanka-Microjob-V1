package com.lanka.user.service;

import com.lanka.user.client.NotificationClient;
import com.lanka.user.dto.*;
import com.lanka.user.model.Admin;
import com.lanka.user.model.User;
import com.lanka.user.repository.AdminRepository;
import com.lanka.user.repository.UserRepository;
import com.lanka.user.security.AuthPrincipal;
import com.lanka.user.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final List<String> SELF_SERVICE_ROLES = List.of("WORKER", "EMPLOYER");

    private final UserRepository users;
    private final AdminRepository admins;
    private final JwtUtil jwt;
    private final PasswordEncoder passwordEncoder;
    private final NotificationClient notifications;
    private final String adminContact;

    public UserService(UserRepository users, AdminRepository admins, JwtUtil jwt, PasswordEncoder passwordEncoder,
                       NotificationClient notifications,
                       @Value("${app.admin.default-email}") String adminContact) {
        this.users = users;
        this.admins = admins;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
        this.adminContact = adminContact;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String mobile = normalizeText(request.mobile());
        String role = normalizeRole(request.role());

        if (!SELF_SERVICE_ROLES.contains(role)) {
            // Defence in depth: the DTO already restricts the role, self-registration can never
            // create an ADMIN (or BROKER, which uses the broker-service approval workflow).
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only WORKER and EMPLOYER accounts can self register");
        }
        if (email != null && users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (mobile != null && users.existsByMobile(mobile)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this mobile number already exists");
        }

        User user = new User();
        user.setName(normalizeText(request.name()));
        user.setMobile(mobile);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setDistrict(normalizeText(request.district()));
        user.setCity(normalizeText(request.city()));
        user.setNic(normalizeText(request.nic()));
        user.setSkills(normalizeSkills(request.skills()));
        user.setAvailability(normalizeText(request.availability()));
        user.setStatus("PENDING");
        User saved = users.save(user);

        notifications.notifyAuto(saved.getContact(), "USER_REGISTERED",
                "Thank you " + saved.getName() + ". Your Lanka MicroJob " + role.toLowerCase()
                        + " account is waiting for admin approval.");
        notifications.notifyAuto(adminContact, "USER_PENDING_APPROVAL",
                "New " + role.toLowerCase() + " registration: " + saved.getName() + " (" + saved.getContact() + ")");
        return new RegisterResponse(saved.getId(), "User registered and waiting for admin approval", saved.getStatus());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String mobile = normalizeText(request.mobile());
        String role = normalizeRole(request.role());

        Optional<User> found = email != null ? users.findByEmail(email) : users.findByMobile(mobile);
        // Always run the password encoder (even for an unknown account) so responses are uniform.
        User user = found.orElse(null);
        boolean passwordMatches = user != null && user.getPassword() != null
                && passwordEncoder.matches(request.password(), user.getPassword());
        if (user == null || !passwordMatches || !role.equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login credentials");
        }
        if (!"APPROVED".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Admin approval required before login (current status: " + user.getStatus() + ")");
        }
        String subject = user.getEmail() != null ? user.getEmail() : user.getMobile();
        // The skills claim lets job-service record a trusted snapshot of the worker's skills on an
        // application without a cross-service call.
        java.util.Map<String, Object> claims = user.getSkills() == null
                ? java.util.Map.of()
                : java.util.Map.of("skills", user.getSkills());
        String token = jwt.generateToken(subject, user.getRole(), user.getId(), user.getName(), claims);
        return new LoginResponse(token, user.getRole(), user.getName(), user.getId(), user.getStatus(),
                user.getDistrict(), user.getCity(), user.getEmail(), user.getMobile(), user.getNic(),
                user.getSkills(), user.getAvailability());
    }

    @Transactional(readOnly = true)
    public AdminLoginResponse adminLogin(AdminLoginRequest request) {
        String gmail = normalizeEmail(request.gmail());
        Admin admin = admins.findByGmail(gmail)
                .filter(found -> found.getPassword() != null
                        && passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials"));
        String token = jwt.generateToken(admin.getGmail(), "ADMIN", admin.getId(), admin.getName());
        return new AdminLoginResponse(token, admin.getName(), "ADMIN", admin.getId());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(AuthPrincipal principal) {
        return toProfile(loadOwnedUser(principal));
    }

    @Transactional(readOnly = true)
    public List<UserApprovalResponse> getPendingUsers() {
        return users.findByStatusOrderByIdDesc("PENDING").stream().map(this::toApprovalResponse).toList();
    }

    /** Admin user directory, optionally filtered by role and/or status. */
    @Transactional(readOnly = true)
    public List<UserApprovalResponse> listUsers(String role, String status) {
        String normalizedRole = normalizeRole(role);
        String normalizedStatus = normalizeText(status) == null ? null : status.trim().toUpperCase();
        List<User> found;
        if (normalizedRole != null && !normalizedRole.isBlank() && normalizedStatus != null) {
            found = users.findByRoleAndStatusOrderByIdDesc(normalizedRole, normalizedStatus);
        } else if (normalizedRole != null && !normalizedRole.isBlank()) {
            found = users.findByRoleOrderByIdDesc(normalizedRole);
        } else if (normalizedStatus != null) {
            found = users.findByStatusOrderByIdDesc(normalizedStatus);
        } else {
            found = users.findAll();
        }
        return found.stream().map(this::toApprovalResponse).toList();
    }

    @Transactional
    public UserApprovalResponse approveUser(Long id) {
        User user = findUser(id);
        if ("APPROVED".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already approved");
        }
        user.setStatus("APPROVED");
        UserApprovalResponse response = toApprovalResponse(users.save(user));
        notifications.notifyAuto(user.getContact(), "USER_APPROVED",
                "Good news " + user.getName() + "! Your Lanka MicroJob account has been approved. You can now log in.");
        log.info("User {} approved", id);
        return response;
    }

    @Transactional
    public UserApprovalResponse rejectUser(Long id) {
        User user = findUser(id);
        if ("REJECTED".equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already rejected");
        }
        user.setStatus("REJECTED");
        UserApprovalResponse response = toApprovalResponse(users.save(user));
        notifications.notifyAuto(user.getContact(), "USER_REJECTED",
                "Your Lanka MicroJob account application was rejected. Please contact support for details.");
        log.info("User {} rejected", id);
        return response;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        return new AdminStatsResponse(
                users.count(),
                users.countByStatus("PENDING"),
                users.countByStatus("APPROVED"),
                users.countByStatus("REJECTED"),
                users.countByRoleAndStatus("WORKER", "PENDING"),
                users.countByRoleAndStatus("EMPLOYER", "PENDING"),
                users.countByRoleAndStatus("WORKER", "APPROVED"),
                users.countByRoleAndStatus("EMPLOYER", "APPROVED"));
    }

    /** Non-sensitive counter used by the public landing page. */
    @Transactional(readOnly = true)
    public Map<String, Long> publicStats() {
        return Map.of("approvedWorkers", users.countByRoleAndStatus("WORKER", "APPROVED"));
    }

    private User loadOwnedUser(AuthPrincipal principal) {
        if (principal == null || principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return findUser(principal.uid());
    }

    private User findUser(Long id) {
        return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserApprovalResponse toApprovalResponse(User user) {
        return new UserApprovalResponse(user.getId(), user.getName(), user.getEmail(), user.getMobile(),
                user.getRole(), user.getDistrict(), user.getCity(), user.getNic(), user.getSkills(),
                user.getAvailability(), user.getStatus(), user.getRegisteredAt());
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getMobile(),
                user.getRole(), user.getDistrict(), user.getCity(), user.getNic(), user.getSkills(),
                user.getAvailability(), user.getStatus());
    }

    /** Keeps the skill list compact and predictable: trimmed, de-duplicated, comma separated. */
    private String normalizeSkills(String value) {
        String text = normalizeText(value);
        if (text == null) return null;
        List<String> skills = java.util.Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isEmpty())
                .distinct()
                .toList();
        return skills.isEmpty() ? null : String.join(",", skills);
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private String normalizeRole(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "" : normalized.toUpperCase();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
