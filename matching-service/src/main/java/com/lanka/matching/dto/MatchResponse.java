package com.lanka.matching.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Score for one job/worker pair.
 *
 * @param score          0-100, rule based (see MatchingService)
 * @param recommendation Strong match / Possible match / Weak match
 * @param matchedSkills  the required skills the worker actually covers
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchResponse(Long jobId, int score, String recommendation, List<String> matchedSkills) {
}
