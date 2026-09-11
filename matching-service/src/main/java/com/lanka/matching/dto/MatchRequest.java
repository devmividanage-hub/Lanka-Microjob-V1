package com.lanka.matching.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One job scored against one worker.
 *
 * @param jobId         job being scored
 * @param district      district of the job
 * @param workerDistrict district of the worker (used for the proximity bonus)
 * @param requiredSkills skills the employer asked for
 * @param workerSkills   skills the worker registered with
 */
public record MatchRequest(
        Long jobId,
        @Size(max = 80) String district,
        @Size(max = 80) String workerDistrict,
        @Size(max = 40, message = "Too many required skills") List<String> requiredSkills,
        @Size(max = 40, message = "Too many worker skills") List<String> workerSkills) {
}
