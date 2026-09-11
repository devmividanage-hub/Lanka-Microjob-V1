package com.lanka.job.security;

/**
 * Authenticated caller resolved from the JWT. {@code uid} is the database id taken from the token,
 * so services never have to trust an id supplied by the browser.
 */
public record AuthPrincipal(Long uid, String identifier, String name, String role, String skills) {
    public AuthPrincipal(Long uid, String identifier, String name, String role) {
        this(uid, identifier, name, role, null);
    }

    public boolean hasRole(String... roles) {
        if (role == null) return false;
        for (String candidate : roles) {
            if (role.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean owns(Long ownerId) {
        return uid != null && uid.equals(ownerId);
    }
}
