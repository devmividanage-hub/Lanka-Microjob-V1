package com.lanka.notification.dto;

import java.util.Map;

/** Real notification counters for the admin dashboard. */
public record NotificationStatsResponse(long total, long simulated, long sent, long failed, Map<String, Long> byType) {
}
