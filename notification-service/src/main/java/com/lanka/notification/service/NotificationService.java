package com.lanka.notification.service;

import com.lanka.notification.dto.NotificationRequest;
import com.lanka.notification.dto.NotificationResponse;
import com.lanka.notification.dto.NotificationStatsResponse;
import com.lanka.notification.model.Notification;
import com.lanka.notification.provider.DeliveryResult;
import com.lanka.notification.provider.NotificationProvider;
import com.lanka.notification.provider.NotificationProviderRegistry;
import com.lanka.notification.repository.NotificationRepository;
import com.lanka.notification.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Records a notification and hands it to the provider registered for its channel.
 *
 * <p>The stored status reflects what actually happened: SIMULATED with the mock providers shipped
 * here, SENT only when a real provider confirms delivery, FAILED when the provider reports an
 * error. The notification row is always persisted first, so the audit trail survives even when a
 * gateway is unreachable.</p>
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationProviderRegistry providers;

    public NotificationService(NotificationRepository notifications, NotificationProviderRegistry providers) {
        this.notifications = notifications;
        this.providers = providers;
    }

    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        String channel = request.channel() == null || request.channel().isBlank()
                ? "EMAIL" : request.channel().trim().toUpperCase(Locale.ROOT);
        Notification notification = new Notification();
        notification.setRecipient(request.recipient().trim());
        notification.setChannel(channel);
        notification.setType(request.type() == null || request.type().isBlank()
                ? "GENERAL" : request.type().trim().toUpperCase(Locale.ROOT));
        notification.setSubject(request.subject());
        notification.setMessage(request.message().trim());
        notification.setCreatedAt(LocalDateTime.now());

        // Persist first so the record exists even if delivery reporting fails.
        Notification saved = notifications.save(notification);

        NotificationProvider provider = providers.forChannel(channel);
        DeliveryResult result;
        try {
            result = provider.deliver(saved.getRecipient(), saved.getSubject() == null ? saved.getType() : saved.getSubject(),
                    saved.getMessage());
        } catch (Exception ex) {
            log.error("Provider {} failed for notification {}", provider.name(), saved.getId(), ex);
            result = new DeliveryResult(false, provider.name(), "FAILED", "Provider error");
        }
        saved.setProvider(result.provider());
        saved.setStatus(result.status());
        saved.setDetail(result.detail());
        if (result.delivered()) {
            saved.setSentAt(LocalDateTime.now());
        }
        return toResponse(notifications.save(saved));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list() {
        return notifications.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(AuthPrincipal principal) {
        String recipient = requireRecipient(principal);
        return notifications.findTop100ByRecipientOrderByIdDesc(recipient).stream().map(this::toResponse).toList();
    }

    /** A caller may only read notifications addressed to their own token subject, unless ADMIN. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForRecipient(String recipient, AuthPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String normalized = recipient == null ? null : recipient.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient is required");
        }
        if (!principal.isAdmin() && !normalized.equalsIgnoreCase(principal.identifier())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only read your own notifications");
        }
        return notifications.findTop100ByRecipientOrderByIdDesc(normalized).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotificationStatsResponse stats() {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (String type : notifications.findDistinctTypes()) {
            byType.put(type, notifications.countByType(type));
        }
        return new NotificationStatsResponse(
                notifications.count(),
                notifications.countByStatus("SIMULATED"),
                notifications.countByStatus("SENT"),
                notifications.countByStatus("FAILED"),
                byType);
    }

    /** Which providers are wired in - used by the docs endpoint so nobody assumes real delivery. */
    public Map<String, String> providers() {
        return providers.describe();
    }

    private String requireRecipient(AuthPrincipal principal) {
        if (principal == null || principal.identifier() == null || principal.identifier().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.identifier();
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getRecipient(), notification.getChannel(),
                notification.getType(), notification.getSubject(), notification.getMessage(), notification.getStatus(),
                notification.getProvider(), notification.getDetail(), notification.getCreatedAt(),
                notification.getSentAt());
    }
}
