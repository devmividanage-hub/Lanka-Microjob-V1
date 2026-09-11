/**
 * Job feed orchestration: fetch the real jobs from job-service, then enrich them with real match
 * scores from matching-service in a single batch call.
 *
 * Nothing here is hardcoded - if the worker has no skills on their profile the match badge is simply
 * omitted rather than invented.
 */
import { jobsApi } from '../api/jobs.js';
import { matchesApi } from '../api/matches.js';
import { skillsList } from '../utils/format.js';
import { currentUser, isApproved, isWorker } from '../auth/session.js';

/** Loads the feed for the given filters. Returns { jobs, error }. */
export async function loadJobs(filters = {}) {
    const jobs = await jobsApi.list({
        district: filters.district,
        city: filters.city,
        category: filters.category,
        search: filters.search,
    });
    return Array.isArray(jobs) ? jobs : [];
}

/** Worker skills as a list; empty when the profile has none. */
export function workerSkills() {
    const user = currentUser();
    return user && user.role === 'WORKER' ? skillsList(user.skills) : [];
}

/**
 * Scores every job in one request. Returns a Map of jobId -> { score, recommendation, matchedSkills }.
 * Skipped (empty map) for anonymous visitors, employers and unapproved workers.
 */
export async function scoreJobs(jobs) {
    const user = currentUser();
    if (!isWorker() || !isApproved(user) || !jobs.length) return new Map();
    const skills = workerSkills();
    if (!skills.length) return new Map();
    try {
        const response = await matchesApi.scoreBatch({
            workerDistrict: user.district || null,
            workerSkills: skills,
            jobs: jobs.map(job => ({
                jobId: job.id,
                district: job.district || null,
                requiredSkills: skillsList(job.requiredSkills),
            })),
        });
        return new Map((response?.results || []).map(entry => [entry.jobId, entry]));
    } catch {
        // Matching is an enhancement: the feed still renders if the matching-service is down.
        return new Map();
    }
}

export async function loadFeedWithMatches(filters = {}) {
    const jobs = await loadJobs(filters);
    const matches = await scoreJobs(jobs);
    return { jobs, matches };
}
