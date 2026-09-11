package com.lanka.user.dto;

/** Real, database-derived counters for the admin dashboard. No static numbers. */
public record AdminStatsResponse(long totalUsers,
                                 long pendingUsers,
                                 long approvedUsers,
                                 long rejectedUsers,
                                 long pendingWorkers,
                                 long pendingEmployers,
                                 long approvedWorkers,
                                 long approvedEmployers) {
}
