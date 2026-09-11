package com.lanka.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies the HS256 JWT shared by every Lanka MicroJob service.
 *
 * <p>The signing key comes from {@code jwt.secret} which is bound to the {@code JWT_SECRET}
 * environment variable; the value in {@code application.properties} is a documented
 * development-only placeholder.</p>
 */
@Component
public class JwtUtil {
    private final Key key;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = expiration;
    }

    public String generateToken(String subject, String role, Long uid, String name) {
        return generateToken(subject, role, uid, name, Map.of());
    }

    public String generateToken(String subject, String role, Long uid, String name, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);
        var builder = Jwts.builder()
                .setSubject(subject)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256);
        if (uid != null) builder.claim("uid", uid);
        if (name != null) builder.claim("name", name);
        extraClaims.forEach(builder::claim);
        return builder.compact();
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
