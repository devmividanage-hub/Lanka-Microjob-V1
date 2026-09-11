package com.lanka.matching.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Scores the whole worker feed in a single round trip: one worker profile plus the list of jobs
 * currently displayed.
 */
public record BatchMatchRequest(
        @Size(max = 80) String workerDistrict,
        @Size(max = 40, message = "Too many worker skills") List<String> workerSkills,
        @Valid @Size(max = 200, message = "Too many jobs to score at once") List<MatchRequest> jobs) {
}
