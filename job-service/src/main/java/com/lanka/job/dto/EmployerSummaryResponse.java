package com.lanka.job.dto;

/** Real counters for the employer dashboard, derived from job and job_applications. */
public record EmployerSummaryResponse(Long employerId,
                                      long totalJobs,
                                      long openJobs,
                                      long assignedJobs,
                                      long inProgressJobs,
                                      long completedJobs,
                                      long cancelledJobs,
                                      long flaggedJobs,
                                      long totalApplications,
                                      long pendingApplications,
                                      long acceptedWorkers,
                                      long rejectedApplications,
                                      long cancelledApplications,
                                      long completedAssignments) {
}
