package com.lanka.notification.provider;

/**
 * Delivery channel abstraction.
 *
 * <p>This project ships two simulated providers so the notification flow can be demonstrated
 * end-to-end without external credentials. Implementing a real gateway is a matter of adding
 * another bean for the same channel: {@link com.lanka.notification.service.NotificationService}
 * picks the provider that reports {@link #isRealDelivery()} first, so a real provider transparently
 * replaces the simulated one and stored notifications start reporting SENT instead of SIMULATED.</p>
 */
public interface NotificationProvider {

    /** Channel this provider serves, e.g. EMAIL or SMS. */
    String channel();

    /** Human readable provider name stored with the notification. */
    String name();

    /** False for the simulated providers in this repository - they never contact a real gateway. */
    boolean isRealDelivery();

    /** Hands the message to the channel and reports what happened. Must not throw. */
    DeliveryResult deliver(String recipient, String subject, String message);
}
