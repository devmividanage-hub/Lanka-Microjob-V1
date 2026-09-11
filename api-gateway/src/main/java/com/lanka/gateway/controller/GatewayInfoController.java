package com.lanka.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small discovery endpoint on the gateway. It documents the public API surface and where each
 * service's OpenAPI document lives, which makes the system easy to explore during a demonstration.
 */
@RestController
public class GatewayInfoController {

    @GetMapping("/")
    Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "Lanka MicroJob API Gateway");
        body.put("status", "UP");
        body.put("routes", Map.of(
                "/auth/**", "user-service (register, login, admin approval, user directory)",
                "/jobs/**", "job-service (job posting, public feed, lifecycle)",
                "/applications/**", "job-service (job applications: apply, accept, reject, cancel, complete)",
                "/matches/**", "matching-service (rule-based skill scoring)",
                "/brokers/**", "broker-service (broker applications, offline workers, commission)",
                "/notifications/**", "notification-service (notification log; delivery is simulated)"));
        body.put("openApi", List.of(
                "/api-docs/user-service", "/api-docs/job-service", "/api-docs/matching-service",
                "/api-docs/broker-service", "/api-docs/notification-service"));
        body.put("health", "/actuator/health");
        return body;
    }
}
