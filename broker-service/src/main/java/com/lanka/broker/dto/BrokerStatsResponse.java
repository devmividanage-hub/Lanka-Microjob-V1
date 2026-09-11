package com.lanka.broker.dto;

import java.util.List;
import java.util.Map;

/** Real broker counters for the admin dashboard, including the per-district breakdown. */
public record BrokerStatsResponse(long totalBrokers, long pendingBrokers, long approvedBrokers, long rejectedBrokers,
                                  long totalOfflineWorkers, long activeOfflineWorkers,
                                  List<Map<String, Object>> byDistrict) {
}
