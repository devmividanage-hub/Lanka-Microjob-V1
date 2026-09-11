package com.lanka.broker.dto;

import java.time.LocalDate;

/** Job-service projection for one real job the selected offline worker may take. */
public record EligibleJobResponse(Long jobId, String title, String employer, Long employerId, String category,
                                  String district, String city, Integer payPerWorker, LocalDate jobDate,
                                  String status, Integer slotsRemaining, String requiredSkills) {
}
