package com.lanka.notification.provider;

/**
 * Outcome of handing a notification to a provider.
 *
 * @param delivered true when the provider accepted the message
 * @param provider  name of the provider implementation that handled it
 * @param status    SIMULATED for the mock providers shipped with this project, SENT only when a
 *                  real gateway confirms delivery, FAILED otherwise
 * @param detail    safe, non-sensitive description written to the log/response
 */
public record DeliveryResult(boolean delivered, String provider, String status, String detail) {
}
