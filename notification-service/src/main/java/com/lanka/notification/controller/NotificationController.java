package com.lanka.notification.controller;

import com.lanka.notification.dto.NotificationRequest;
import com.lanka.notification.dto.NotificationResponse;
import com.lanka.notification.dto.NotificationStatsResponse;
import com.lanka.notification.security.SecurityUtils;
import com.lanka.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@Tag(name = "Notifications", description = "Notification log. Delivery is simulated in this project.")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Record a notification (internal services with X-Internal-Token, or ADMIN)")
    NotificationResponse send(@Valid @RequestBody NotificationRequest request) {
        return service.send(request);
    }

    @GetMapping("/mine")
    @Operation(summary = "Notifications addressed to the authenticated caller")
    List<NotificationResponse> mine() {
        return service.listMine(SecurityUtils.require());
    }

    @GetMapping("/recipient/{recipient}")
    @Operation(summary = "Notifications for one recipient (that recipient or ADMIN)")
    List<NotificationResponse> forRecipient(@PathVariable String recipient) {
        return service.listForRecipient(recipient, SecurityUtils.require());
    }

    @GetMapping
    @Operation(summary = "Latest notifications across the platform (ADMIN only)")
    List<NotificationResponse> list() {
        return service.list();
    }

    @GetMapping("/stats")
    @Operation(summary = "Real notification counters (ADMIN only)")
    NotificationStatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/providers")
    @Operation(summary = "Which providers are configured. SIMULATED means no real delivery happens.")
    Map<String, String> providers() {
        return service.providers();
    }
}
