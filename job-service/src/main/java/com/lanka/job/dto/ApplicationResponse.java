package com.lanka.job.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Application plus the job details the dashboards need, so the frontend does not have to join
 * two calls to render a row.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicationResponse(Long id, Long jobId, String jobTitle, String employer, Long employerId,
                                  String category, String district, String city, Integer payPerWorker,
                                  LocalDate jobDate, String jobStatus, Long workerId, String workerName,
                                  String workerEmail, String workerSkills, String message, String status,
                                  LocalDateTime appliedAt, LocalDateTime updatedAt) {
}
