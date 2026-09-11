package com.lanka.broker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Everything the broker dashboard renders, all of it computed from the database.
 *
 * <p>{@code averageRating} is null while no offline worker has been rated, so the UI shows
 * "no ratings yet" instead of a made-up number.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrokerDashboardResponse(String brokerId, Long id, String name, String email, String phone,
                                      String district, String city, String status, int workerCount,
                                      long activeWorkers, long onJobWorkers, long inactiveWorkers,
                                      long totalPlacements, long commissionEarned, Double averageRating,
                                      double commissionRate, List<OfflineWorkerResponse> workers) {
}
