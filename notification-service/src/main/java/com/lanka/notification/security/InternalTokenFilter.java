package com.lanka.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates service-to-service writes with a shared secret in {@code X-Internal-Token}.
 *
 * <p>user-service, job-service and broker-service raise notifications after committing business
 * changes; those calls carry no user JWT, so they present the internal token instead and are
 * granted the SYSTEM role. The token is compared in constant time and is read from
 * {@code INTERNAL_SERVICE_TOKEN}.</p>
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Token";

    private final byte[] expected;

    public InternalTokenFilter(@Value("${notification.internal.token:}") String token) {
        this.expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && expected.length > 0
                && MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8), expected)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthPrincipal principal = new AuthPrincipal(null, "internal-service", "Internal Service", "SYSTEM", null);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
        }
        chain.doFilter(request, response);
    }
}
