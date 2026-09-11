package com.lanka.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulated email provider.
 *
 * <p>No mail server is contacted. The message is written to the application log and the stored
 * notification is marked SIMULATED so nobody can mistake this for a delivered email. To send real
 * mail, add another {@code EMAIL} provider that talks to an SMTP server and reports
 * {@code isRealDelivery() == true}; it will be preferred automatically.</p>
 */
@Component
public class EmailNotificationProvider implements NotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationProvider.class);
    private static final String NAME = "SimulatedEmailProvider";

    private final boolean enabled;

    public EmailNotificationProvider(@Value("${notification.provider.email:SIMULATED}") String mode) {
        this.enabled = !"DISABLED".equalsIgnoreCase(mode);
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isRealDelivery() {
        return false;
    }

    @Override
    public DeliveryResult deliver(String recipient, String subject, String message) {
        if (!enabled) {
            return new DeliveryResult(false, NAME, "DISABLED", "Email provider is disabled by configuration");
        }
        log.info("[SIMULATED EMAIL - NOT SENT] to={} subject={} body={}", recipient, subject, message);
        return new DeliveryResult(true, NAME, "SIMULATED", "Logged only; no mail server is configured");
    }
}
