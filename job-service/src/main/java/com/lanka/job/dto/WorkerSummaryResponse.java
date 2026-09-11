package com.lanka.job.dto;

/** Real counters for the worker dashboard, derived from job_applications and job. */
public record WorkerSummaryResponse(Long workerId,
                                    long availableJobs,
                                    long totalApplications,
                                    long applied,
                                    long accepted,
                                    long rejected,
                                    long cancelled,
                                    long completed) {
}
