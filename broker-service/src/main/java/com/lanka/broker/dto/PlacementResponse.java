package com.lanka.broker.dto;

/** Result of a real job placement plus the freshly updated broker/worker totals. */
public record PlacementResponse(PlacementRecordResponse placement, OfflineWorkerResponse worker,
                                long commissionForPlacement, long brokerCommissionTotal,
                                long brokerPlacementsTotal) {
}
