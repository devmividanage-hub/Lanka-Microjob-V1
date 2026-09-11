package com.lanka.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT security for the user-service.
 *
 * <p>CORS origins come from {@code app.cors.allowed-origins} (env {@code CORS_ALLOWED_ORIGINS}).
 * Development defaults allow the local Vite/nginx origins; set the real frontend origin(s) in
 * production instead of {@code *}.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    /** Paths that must stay reachable without a token: public auth endpoints, health and API docs. */
    private static final String[] PUBLIC_PATHS = {
            "/auth/register", "/auth/login", "/auth/admin/login",
            "/auth/public-stats",
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    private final JwtFilter jwtFilter;
    private final List<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtFilter jwtFilter,
                          @Value("${app.cors.allowed-origins}") String allowedOrigins,
                          ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "You do not have permission to perform this action")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Administrative endpoints: only an ADMIN token may review users.
                        .requestMatchers("/auth/users/**", "/auth/stats").hasRole("ADMIN")
                        // Everything else (e.g. /auth/me) needs any valid token.
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", status == 401 ? "Unauthorized" : "Forbidden");
        body.put("message", message);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        if (allowedOrigins.contains("*")) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(allowedOrigins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
