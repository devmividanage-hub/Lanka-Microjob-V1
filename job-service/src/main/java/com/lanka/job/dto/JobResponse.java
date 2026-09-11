package com.lanka.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Job as returned to clients. {@code applicationCount} is only filled for employer/admin views. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(Long id, String title, String employer, Long employerId, String category, String district,
                          String city, Integer workersNeeded, Integer payPerWorker, LocalDate jobDate, String status,
                          Boolean urgent, Integer slotsRemaining, String requiredSkills, String additionalNotes,
                          Integer applicationCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
