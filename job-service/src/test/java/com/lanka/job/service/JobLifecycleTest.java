package com.lanka.job.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the job status machine that gates applications and employer actions.
 *
 * <p>These rules are the reason a closed job cannot receive new applications, so they are tested
 * directly rather than only through HTTP.</p>
 */
class JobLifecycleTest {

    @Test
    @DisplayName("the happy path OPEN -> ASSIGNED -> IN_PROGRESS -> COMPLETED is allowed")
    void happyPath() {
        assertTrue(JobLifecycle.canTransition(JobLifecycle.OPEN, JobLifecycle.ASSIGNED));
        assertTrue(JobLifecycle.canTransition(JobLifecycle.ASSIGNED, JobLifecycle.IN_PROGRESS));
        assertTrue(JobLifecycle.canTransition(JobLifecycle.IN_PROGRESS, JobLifecycle.COMPLETED));
    }

    @Test
    @DisplayName("an open job can be cancelled, expire or be flagged by an admin")
    void openJobAlternatives() {
        assertTrue(JobLifecycle.canTransition(JobLifecycle.OPEN, JobLifecycle.CANCELLED));
        assertTrue(JobLifecycle.canTransition(JobLifecycle.OPEN, JobLifecycle.EXPIRED));
        assertTrue(JobLifecycle.canTransition(JobLifecycle.OPEN, JobLifecycle.FLAGGED));
    }

    @Test
    @DisplayName("a flagged job can be returned to the feed or cancelled, nothing else")
    void flaggedJobRecovery() {
        assertTrue(JobLifecycle.canTransition(JobLifecycle.FLAGGED, JobLifecycle.OPEN));
        assertTrue(JobLifecycle.canTransition(JobLifecycle.FLAGGED, JobLifecycle.CANCELLED));
        assertFalse(JobLifecycle.canTransition(JobLifecycle.FLAGGED, JobLifecycle.COMPLETED));
    }

    @Test
    @DisplayName("terminal statuses can never change again")
    void terminalStatusesAreFrozen() {
        for (String terminal : JobLifecycle.TERMINAL) {
            assertTrue(JobLifecycle.isTerminal(terminal));
            for (String target : JobLifecycle.ALL) {
                assertFalse(JobLifecycle.canTransition(terminal, target),
                        terminal + " must not transition to " + target);
            }
        }
    }

    @Test
    @DisplayName("a completed job cannot be reopened, so no late applications can be accepted")
    void completedCannotReopen() {
        assertFalse(JobLifecycle.canTransition(JobLifecycle.COMPLETED, JobLifecycle.OPEN));
        assertFalse(JobLifecycle.canTransition(JobLifecycle.CANCELLED, JobLifecycle.OPEN));
    }

    @Test
    @DisplayName("an assigned job may skip straight to completed when the work is short")
    void assignedCanComplete() {
        assertTrue(JobLifecycle.canTransition(JobLifecycle.ASSIGNED, JobLifecycle.COMPLETED));
    }

    @Test
    @DisplayName("legacy COMPLETE values are normalised to COMPLETED")
    void normalizesLegacyValue() {
        assertEquals(JobLifecycle.COMPLETED, JobLifecycle.normalize("COMPLETE"));
        assertEquals(JobLifecycle.COMPLETED, JobLifecycle.normalize(" complete "));
        assertEquals(JobLifecycle.OPEN, JobLifecycle.normalize("open"));
    }

    @Test
    @DisplayName("a missing or blank status defaults to OPEN instead of failing")
    void defaultsToOpen() {
        assertEquals(JobLifecycle.OPEN, JobLifecycle.normalize(null));
        assertEquals(JobLifecycle.OPEN, JobLifecycle.normalize("   "));
        assertTrue(JobLifecycle.isOpen(null));
    }

    @Test
    @DisplayName("unknown statuses are rejected rather than silently accepted")
    void unknownStatusRejected() {
        assertFalse(JobLifecycle.isKnown("PUBLISHED"));
        assertFalse(JobLifecycle.canTransition(JobLifecycle.OPEN, "PUBLISHED"));
    }

    @Test
    @DisplayName("only OPEN jobs are applyable")
    void onlyOpenIsApplyable() {
        assertEquals(1, JobLifecycle.APPLYABLE.size());
        assertTrue(JobLifecycle.APPLYABLE.contains(JobLifecycle.OPEN));
        assertFalse(JobLifecycle.APPLYABLE.contains(JobLifecycle.ASSIGNED));
    }

    @Test
    @DisplayName("every blocked-application reason is a real sentence, used in 409 responses")
    void blockedApplicationReasons() {
        assertEquals("This job was cancelled", JobLifecycle.describeBlockedApplication("CANCELLED"));
        assertEquals("This job has expired", JobLifecycle.describeBlockedApplication("EXPIRED"));
        assertEquals("This job is already completed", JobLifecycle.describeBlockedApplication("COMPLETED"));
        assertEquals("This job is under review by the platform team",
                JobLifecycle.describeBlockedApplication("FLAGGED"));
        assertEquals("This job is no longer accepting applications",
                JobLifecycle.describeBlockedApplication("ASSIGNED"));
        assertEquals("This job is not open for applications",
                JobLifecycle.describeBlockedApplication("SOMETHING_ELSE"));
    }
}
