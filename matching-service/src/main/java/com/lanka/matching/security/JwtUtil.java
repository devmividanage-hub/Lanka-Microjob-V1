package com.lanka.matching.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

/**
 * Verifies the HS256 JWT issued by the user-service (and the broker-service for BROKER tokens).
 * The signing key is read from {@code jwt.secret}, bound to the {@code JWT_SECRET} environment
 * variable; the value in application.properties is a development-only placeholder.
 */
@Component
public class JwtUtil {
    private final Key key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }

    public String extractRole(String token) {
        return parse(token).get("role", String.class);
    }

    public String extractName(String token) {
        return parse(token).get("name", String.class);
    }

    public Long extractUid(String token) {
        Object uid = parse(token).get("uid");
        return uid == null ? null : Long.valueOf(uid.toString());
    }

    public String extractClaim(String token, String name) {
        Object value = parse(token).get(name);
        return value == null ? null : value.toString();
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
