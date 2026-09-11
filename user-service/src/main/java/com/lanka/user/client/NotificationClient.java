package com.lanka.user.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/**
 * Fire-and-forget client for the notification-service.
 *
 * <p>Notification delivery must never break a business transaction, so every call is wrapped and
 * failures are logged only. Writes are authenticated with a shared internal service token
 * ({@code X-Internal-Token}) rather than a user JWT because these are service-to-service calls.</p>
 */
@Component
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient restClient;
    private final String internalToken;
    private final boolean enabled;

    public NotificationClient(@Value("${notification.service.url:}") String baseUrl,
                              @Value("${notification.internal.token:}") String internalToken,
                              @Value("${notification.client.enabled:true}") boolean enabled) {
        this.internalToken = internalToken;
        this.enabled = enabled && baseUrl != null && !baseUrl.isBlank();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.restClient = enabled ? RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build() : null;
    }

    /**
     * Records a notification for a recipient.
     *
     * @param recipient email address or mobile number
     * @param channel   EMAIL or SMS
     * @param type      event type, e.g. USER_APPROVED
     * @param message   human readable text
     */
    public void notify(String recipient, String channel, String type, String message) {
        if (!enabled || recipient == null || recipient.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalToken)
                    .body(Map.of(
                            "recipient", recipient,
                            "channel", channel == null ? "EMAIL" : channel,
                            "type", type == null ? "GENERAL" : type,
                            "message", message == null ? "" : message))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Notification queued: type={} recipient={}", type, recipient);
        } catch (Exception ex) {
            // Deliberately swallowed: the notification-service is a best-effort side channel.
            log.warn("Could not deliver {} notification to {}: {}", type, recipient, ex.getMessage());
        }
    }

    /** Convenience helper: pick the right channel for the recipient and notify. */
    public void notifyAuto(String recipient, String type, String message) {
        if (recipient == null) return;
        notify(recipient, recipient.contains("@") ? "EMAIL" : "SMS", type, message);
    }
}
