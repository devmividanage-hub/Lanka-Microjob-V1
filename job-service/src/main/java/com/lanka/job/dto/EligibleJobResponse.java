package com.lanka.job.dto;

import java.time.LocalDate;

/** Real OPEN job that passed the server-side offline-worker eligibility rules. */
public record EligibleJobResponse(Long jobId, String title, String employer, Long employerId, String category,
                                  String district, String city, Integer payPerWorker, LocalDate jobDate,
                                  String status, Integer slotsRemaining, String requiredSkills) {
}
