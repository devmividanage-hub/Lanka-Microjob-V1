package com.lanka.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulated SMS provider.
 *
 * <p>No SMS gateway is contacted and no credit is consumed. The message is written to the log and
 * the stored notification is marked SIMULATED. Replace with a real gateway implementation when the
 * project has provider credentials.</p>
 */
@Component
public class SmsNotificationProvider implements NotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(SmsNotificationProvider.class);
    private static final String NAME = "SimulatedSmsProvider";

    private final boolean enabled;

    public SmsNotificationProvider(@Value("${notification.provider.sms:SIMULATED}") String mode) {
        this.enabled = !"DISABLED".equalsIgnoreCase(mode);
    }

    @Override
    public String channel() {
        return "SMS";
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
            return new DeliveryResult(false, NAME, "DISABLED", "SMS provider is disabled by configuration");
        }
        log.info("[SIMULATED SMS - NOT SENT] to={} body={}", recipient, message);
        return new DeliveryResult(true, NAME, "SIMULATED", "Logged only; no SMS gateway is configured");
    }
}
