package com.lanka.broker.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Helpers for reading the authenticated {@link AuthPrincipal} inside controllers/services. */
public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static Optional<AuthPrincipal> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static AuthPrincipal require() {
        return current().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
    }

    public static Long requireUid() {
        AuthPrincipal principal = require();
        if (principal.uid() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token does not identify a user");
        }
        return principal.uid();
    }
}
