/** Renders one job card in the worker feed, including the real match score from matching-service. */
import { esc } from '../utils/dom.js';
import { jobStatusBadge, matchTone, money, shortDate, skillsList } from '../utils/format.js';

const feedMoney = amount => money(amount).replace(/^Rs\./, 'Rs. ');

export function matchBadge(match) {
    if (!match || typeof match.score !== 'number') return '';
    const tone = matchTone(match.score);
    const matched = (match.matchedSkills || []).slice(0, 3).map(esc).join(', ');
    const title = matched ? `Matching skills: ${matched}` : match.recommendation || '';
    const score = Math.max(0, Math.min(100, Number(match.score)));
    return `<div class="match-block match-${tone}" title="${esc(title)}">
        <div class="match-heading">
            <div>
                <span class="match-caption">Match score</span>
                <div class="match-result"><strong class="match-score">${score}%</strong>
                    <span class="match-label">${esc(match.recommendation || 'Match')}</span></div>
            </div>
            <small>Rule-based skill match</small>
        </div>
        <div class="match-progress" role="progressbar" aria-label="Job match score" aria-valuemin="0"
             aria-valuemax="100" aria-valuenow="${score}"><span style="width:${score}%"></span></div>
    </div>`;
}

/**
 * @param job         job row from GET /jobs
 * @param match       score from POST /matches/batch (may be undefined)
 * @param state       { canApply, applied, isOwner, applicationStatusLabel }
 */
export function jobCard(job, match, state = {}) {
    const skills = skillsList(job.requiredSkills);
    const category = job.category || 'General';
    const usefulSkills = skills.filter(skill => skill.toLocaleLowerCase() !== category.toLocaleLowerCase());
    const slots = Number(job.slotsRemaining ?? job.workersNeeded ?? 0);
    const full = slots <= 0;
    const open = String(job.status).toUpperCase() === 'OPEN';
    const applicationStatus = state.applicationStatus || '';
    const location = [job.city, job.district].filter(Boolean).join(', ') || 'Location not specified';

    let action;
    if (state.isOwner) {
        action = `<button class="btn btn-amber btn-sm" onclick="lmjViewApplications(${job.id})">View applications</button>`;
    } else if (applicationStatus || state.applied) {
        const label = String(applicationStatus).toUpperCase() === 'ACCEPTED' ? 'Accepted' : 'Applied';
        action = `<span class="applied-pill">${esc(state.applicationStatusLabel || label)}</span>`;
    } else if (state.canApply && open && !full) {
        action = `<button class="btn btn-primary btn-sm job-apply-button" onclick="lmjApply(${job.id}, this)">Apply Now</button>`;
    } else {
        const label = full ? 'Job Full' : !open ? 'Not Open' : 'Apply Now';
        const reason = full ? 'This job has no remaining slots' : !open ? 'This job is not open' : 'Sign in as an approved worker to apply';
        action = `<button class="btn btn-primary btn-sm job-apply-button" disabled title="${esc(reason)}">${label}</button>`;
    }

    return `
    <article class="job-card${applicationStatus || state.applied ? ' accepted' : ''}" data-job-id="${job.id}">
      <div class="job-top">
        <div class="job-identity">
          <h3 class="job-title">${esc(job.title)}</h3>
          <div class="job-emp">${esc(job.employer || 'Employer')}</div>
          <div class="job-location">${esc(location)}</div>
        </div>
        <div class="job-card-aside">
          <div class="job-pay"><strong>${feedMoney(job.payPerWorker)}</strong><span>per day</span></div>
          <div class="job-status-row">${jobStatusBadge(job.status)}
            ${job.urgent ? '<span class="urgent-flag">Urgent</span>' : ''}</div>
        </div>
      </div>
      <div class="job-tags job-primary-tags">
        <span class="tag tag-blue">${esc(category)}</span>
      </div>
      ${usefulSkills.length ? `<div class="job-skills"><span class="job-skills-label">Required skills</span>
        <div class="job-tags">${usefulSkills.slice(0, 4).map(skill => `<span class="tag tag-purple">${esc(skill)}</span>`).join('')}</div></div>` : ''}
      ${match ? matchBadge(match) : ''}
      <div class="job-footer">
        <div class="job-meta">
          <span class="job-meta-item"><span class="status-dot ${slots > 2 ? 'dot-green' : 'dot-amber'}"></span>${slots} slot${slots === 1 ? '' : 's'} left</span>
          <span class="job-meta-separator" aria-hidden="true">&middot;</span>
          <span class="job-meta-item">${shortDate(job.jobDate)}</span>
          ${job.city ? `<span class="job-meta-separator" aria-hidden="true">&middot;</span><span class="job-meta-item">${esc(job.city)}</span>` : ''}
        </div>
        <div class="job-actions">
          <button class="btn btn-secondary btn-sm" onclick="lmjJobDetail(${job.id})">View Details</button>
          ${action}
        </div>
      </div>
    </article>`;
}

export function jobCardList(jobs, matches, state) {
    return jobs.map(job => jobCard(job, matches?.get?.(job.id), {
        ...state,
        applicationStatus: state?.applicationStatusFor?.(job.id) || '',
    })).join('');
}
