package com.lanka.matching.service;

import com.lanka.matching.dto.MatchRequest;
import com.lanka.matching.dto.MatchResponse;
import com.lanka.matching.model.MatchResult;
import com.lanka.matching.repository.MatchResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rule-based worker/job matching.
 *
 * <p><strong>This is not machine learning.</strong> It is a transparent, deterministic scoring rule
 * that is easy to explain and test:</p>
 * <ol>
 *   <li>Normalise both skill lists (trim, lower-case, de-duplicate).</li>
 *   <li>A job that lists "No Experience Needed" is treated as satisfied by any worker.</li>
 *   <li>{@code coverage = matched / required}; a job with no required skills scores a neutral 50%.</li>
 *   <li>{@code score = round(coverage * baseWeight) + (sameDistrict ? districtBonus : 0)}, capped to 0-100.</li>
 *   <li>Label: score &gt;= 70 "Strong match", &gt;= 40 "Possible match", otherwise "Weak match".</li>
 * </ol>
 *
 * <p>Defaults: baseWeight 90, districtBonus 10, so a full skill match in the worker's own district
 * scores 100 and a full match elsewhere scores 90.</p>
 */
@Service
public class MatchingService {
    private static final Set<String> NO_EXPERIENCE_MARKERS = Set.of("no experience", "no experience needed",
            "no experience required", "none");

    private final MatchResultRepository results;
    private final int baseWeight;
    private final int districtBonus;

    public MatchingService(MatchResultRepository results,
                           @Value("${app.matching.base-weight:90}") int baseWeight,
                           @Value("${app.matching.district-bonus:10}") int districtBonus) {
        this.results = results;
        this.baseWeight = baseWeight;
        this.districtBonus = districtBonus;
    }

    /** Scores one job for one worker and stores the outcome as an auditable match history row. */
    @Transactional
    public MatchResponse match(MatchRequest request) {
        MatchResponse response = score(request.jobId(), request.district(), request.workerDistrict(),
                request.requiredSkills(), request.workerSkills());
        MatchResult result = new MatchResult();
        result.setJobId(request.jobId());
        result.setDistrict(request.district());
        result.setScore(response.score());
        result.setRecommendation(response.recommendation());
        result.setWorkerSkills(normalize(request.workerSkills()));
        result.setMatchedAt(LocalDateTime.now());
        results.save(result);
        return response;
    }

    /**
     * Scores many jobs at once for the worker feed. Deliberately does <em>not</em> persist anything:
     * a feed refresh would otherwise write one row per job per view.
     */
    public List<MatchResponse> matchAll(String workerDistrict, List<String> workerSkills, List<MatchRequest> jobs) {
        List<MatchResponse> scored = new ArrayList<>();
        if (jobs == null) {
            return scored;
        }
        for (MatchRequest job : jobs) {
            scored.add(score(job.jobId(), job.district(), workerDistrict, job.requiredSkills(), workerSkills));
        }
        return scored;
    }

    @Transactional(readOnly = true)
    public List<MatchResult> getByJobId(Long jobId) {
        return results.findByJobIdOrderByIdDesc(jobId);
    }

    MatchResponse score(Long jobId, String jobDistrict, String workerDistrict, List<String> requiredSkills,
                        List<String> workerSkills) {
        Set<String> required = normalizeSet(requiredSkills);
        Set<String> worker = normalizeSet(workerSkills);

        List<String> matched = new ArrayList<>();
        int score;
        if (required.isEmpty()) {
            // No skill requirement published: a neutral score keeps such jobs visible but not "perfect".
            score = 50;
        } else {
            // Iterate the original list so the response keeps the employer's own skill wording.
            List<String> originalRequired = requiredSkills == null ? List.of() : requiredSkills.stream()
                    .filter(this::notBlank).map(String::trim).distinct().toList();
            int realRequirements = 0;
            for (String skill : originalRequired) {
                String key = skill.toLowerCase();
                boolean noExperience = NO_EXPERIENCE_MARKERS.contains(key);
                if (noExperience || worker.contains(key)) {
                    matched.add(skill);
                }
                if (!noExperience) {
                    realRequirements++;
                }
            }
            long covered = matched.stream().filter(skill -> !NO_EXPERIENCE_MARKERS.contains(skill.toLowerCase())).count();
            score = realRequirements == 0
                    ? baseWeight
                    : (int) Math.round(((double) covered / realRequirements) * baseWeight);
        }

        if (districtBonus > 0 && notBlank(jobDistrict) && jobDistrict.trim().equalsIgnoreCase(trim(workerDistrict))) {
            score += districtBonus;
        }
        score = Math.max(0, Math.min(100, score));

        return new MatchResponse(jobId, score, label(score), matched);
    }

    static String label(int score) {
        if (score >= 70) return "Strong match";
        if (score >= 40) return "Possible match";
        return "Weak match";
    }

    private Set<String> normalizeSet(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values == null) return normalized;
        for (String value : values) {
            String clean = trim(value);
            if (clean != null) normalized.add(clean.toLowerCase());
        }
        return normalized;
    }

    /** Comma separated form used when persisting a match result. */
    private String normalize(List<String> values) {
        Set<String> normalized = normalizeSet(values);
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
