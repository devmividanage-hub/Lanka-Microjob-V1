package com.lanka.job.security;

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

/** Authenticates private broker-service calls; the shared token is never sent to a browser. */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public InternalTokenFilter(@Value("${notification.internal.token:}") String token) {
        expected = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Internal-Token");
        if (supplied != null && expected.length > 0
                && MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthPrincipal principal = new AuthPrincipal(null, "internal-service", "Internal Service", "SYSTEM");
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
        }
        chain.doFilter(request, response);
    }
}
