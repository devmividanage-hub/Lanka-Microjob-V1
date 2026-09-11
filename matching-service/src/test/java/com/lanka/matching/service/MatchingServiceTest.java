package com.lanka.matching.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the rule-based scoring engine.
 *
 * <p>The repository is passed as {@code null} because {@code score()} is a pure function - these
 * tests deliberately cover the matching rules without a database, so they stay fast and exact.</p>
 *
 * <p>Default weights under test: baseWeight 90, districtBonus 10.</p>
 */
class MatchingServiceTest {

    private final MatchingService service = new MatchingService(null, 90, 10);

    @Test
    @DisplayName("full skill coverage in the same district scores 100 and is a strong match")
    void fullCoverageSameDistrict() {
        var response = service.score(1L, "Colombo", "Colombo",
                List.of("Masonry", "Painting"), List.of("masonry", "painting", "tiling"));

        assertEquals(100, response.score());
        assertEquals("Strong match", response.recommendation());
    }

    @Test
    @DisplayName("full skill coverage in another district scores the base weight only")
    void fullCoverageOtherDistrict() {
        var response = service.score(2L, "Galle", "Colombo",
                List.of("Masonry"), List.of("masonry"));

        assertEquals(90, response.score());
        assertEquals("Strong match", response.recommendation());
    }

    @Test
    @DisplayName("partial coverage is proportional: 1 of 2 skills scores 45 (+10 district bonus)")
    void partialCoverage() {
        var response = service.score(3L, "Kandy", "Kandy",
                List.of("Masonry", "Welding"), List.of("masonry"));

        assertEquals(55, response.score(), "round(0.5 * 90) = 45, plus the 10 point district bonus");
        assertEquals("Possible match", response.recommendation());
    }

    @Test
    @DisplayName("no shared skills scores only the district bonus and is a weak match")
    void noCoverage() {
        var response = service.score(4L, "Jaffna", "Jaffna",
                List.of("Welding", "Electrical"), List.of("cleaning"));

        assertEquals(10, response.score());
        assertEquals("Weak match", response.recommendation());
    }

    @Test
    @DisplayName("a job with no required skills gets the neutral score of 50")
    void noRequirementsIsNeutral() {
        var response = service.score(5L, "Colombo", "Galle", List.of(), List.of("masonry"));

        assertEquals(50, response.score());
        assertEquals("Possible match", response.recommendation());
    }

    @Test
    @DisplayName("\"No Experience Needed\" is satisfied by every worker and does not penalise the score")
    void noExperienceNeededIsWildcard() {
        var response = service.score(6L, "Matara", "Matara",
                List.of("No Experience Needed"), List.of());

        assertEquals(100, response.score(), "base weight 90 for the wildcard plus the district bonus");
        assertEquals("Strong match", response.recommendation());
    }

    @Test
    @DisplayName("skill comparison ignores case, padding and duplicates")
    void normalisationIsApplied() {
        var response = service.score(7L, "Colombo", "colombo",
                List.of("  MASOMETRY  ".replace("MASOMETRY", "Masonry"), "masonry"), List.of(" MASONRY "));

        assertEquals(100, response.score());
    }

    @Test
    @DisplayName("a worker with no skills at all scores 0 outside their district")
    void emptyWorkerSkills() {
        var response = service.score(8L, "Colombo", "Galle", List.of("Masonry"), List.of());

        assertEquals(0, response.score());
    }

    @Test
    @DisplayName("null skill lists are handled instead of throwing")
    void nullListsAreSafe() {
        var response = service.score(9L, null, null, null, null);

        assertEquals(50, response.score(), "no requirements published means the neutral score");
    }

    @Test
    @DisplayName("the score never exceeds 100 even with generous weights")
    void scoreIsCapped() {
        MatchingService generous = new MatchingService(null, 100, 50);
        var response = generous.score(10L, "Colombo", "Colombo", List.of("Masonry"), List.of("masonry"));

        assertEquals(100, response.score());
        assertTrue(response.score() <= 100);
    }

    @Test
    @DisplayName("recommendation labels follow the documented thresholds")
    void labelThresholds() {
        assertEquals("Strong match", MatchingService.label(70));
        assertEquals("Strong match", MatchingService.label(100));
        assertEquals("Possible match", MatchingService.label(69));
        assertEquals("Possible match", MatchingService.label(40));
        assertEquals("Weak match", MatchingService.label(39));
        assertEquals("Weak match", MatchingService.label(0));
    }
}
