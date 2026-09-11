package com.lanka.job.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The job status machine.
 *
 * <pre>
 *   OPEN ──(all slots accepted)──> ASSIGNED ──> IN_PROGRESS ──> COMPLETED
 *    │                                │              │
 *    └────────────> CANCELLED <───────┴──────────────┘
 *    └────────────> EXPIRED   (jobDate has passed, resolved lazily on read)
 *    └────────────> FLAGGED   (admin moderation) ──> OPEN | CANCELLED
 * </pre>
 *
 * <p>Only rule-based transitions are allowed; the service layer rejects anything else with 409.</p>
 */
public final class JobLifecycle {
    public static final String OPEN = "OPEN";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";
    public static final String FLAGGED = "FLAGGED";

    /** Statuses a job can be in while workers may still apply. */
    public static final Set<String> APPLYABLE = Set.of(OPEN);

    /** Statuses that can never change again. */
    public static final Set<String> TERMINAL = Set.of(COMPLETED, CANCELLED, EXPIRED);

    public static final List<String> ALL = List.of(OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED, FLAGGED);

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            OPEN, Set.of(ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED, FLAGGED),
            ASSIGNED, Set.of(IN_PROGRESS, COMPLETED, CANCELLED, FLAGGED),
            IN_PROGRESS, Set.of(COMPLETED, CANCELLED, FLAGGED),
            FLAGGED, Set.of(OPEN, CANCELLED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            EXPIRED, Set.of());

    private JobLifecycle() {
    }

    /**
     * Normalises a stored status. Older builds wrote {@code COMPLETE}; everything else is
     * upper-cased so comparisons stay predictable.
     */
    public static String normalize(String status) {
        if (status == null || status.isBlank()) return OPEN;
        String value = status.trim().toUpperCase();
        return "COMPLETE".equals(value) ? COMPLETED : value;
    }

    public static boolean isKnown(String status) {
        return ALL.contains(normalize(status));
    }

    public static boolean isTerminal(String status) {
        return TERMINAL.contains(normalize(status));
    }

    public static boolean isOpen(String status) {
        return OPEN.equals(normalize(status));
    }

    public static boolean canTransition(String from, String to) {
        String target = normalize(to);
        if (!isKnown(target)) return false;
        return ALLOWED.getOrDefault(normalize(from), Set.of()).contains(target);
    }

    /** Human readable reason used in 409 responses. */
    public static String describeBlockedApplication(String status) {
        return switch (normalize(status)) {
            case CANCELLED -> "This job was cancelled";
            case EXPIRED -> "This job has expired";
            case COMPLETED -> "This job is already completed";
            case FLAGGED -> "This job is under review by the platform team";
            case ASSIGNED, IN_PROGRESS -> "This job is no longer accepting applications";
            default -> "This job is not open for applications";
        };
    }
}
