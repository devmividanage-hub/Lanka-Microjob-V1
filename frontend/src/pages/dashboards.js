/**
 * Worker and employer dashboards.
 *
 * Every number on these pages comes from the backend: GET /applications/summary/worker,
 * GET /applications/summary/employer, GET /jobs/mine and GET /applications/mine.
 */
import { applicationsApi } from '../api/applications.js';
import { authApi } from '../api/auth.js';
import { jobsApi } from '../api/jobs.js';
import { currentUser, isApproved, session } from '../auth/session.js';
import { employerApplicationCard, workerApplicationCard } from '../components/applicationCard.js';
import { kpiRow, sectionTitle } from '../components/stats.js';
import { navigate } from '../services/router.js';
import { $, emptyBlock, errorBlock, esc, loadingBlock, setHtml, setText, show, toast, withBusyButton } from '../utils/dom.js';
import { dateTime, jobStatusBadge, money, shortDate } from '../utils/format.js';

// --------------------------------------------------------------------------- worker dashboard

export async function loadWorkerDashboard() {
    const user = currentUser();
    if (!user) return;
    setText('#worker-dash-name', user.name || 'Worker');
    setText('#worker-dash-city', [user.city, user.district].filter(Boolean).join(', ') || 'City not set');
    setText('#worker-status-kpi', user.status || 'PENDING');
    setText('#worker-city-kpi', user.city || user.district || '—');
    setText('#worker-approval-title', isApproved(user) ? 'Approved worker account' : 'Admin approval required');
    setText('#worker-approval-text', isApproved(user)
        ? 'You can browse the feed and apply for jobs in your district.'
        : 'Your account is PENDING. An administrator must approve it before you can apply.');
    show('#worker-approved-only', isApproved(user));
    show('#worker-pending-only', !isApproved(user));

    const statsEl = $('#worker-stats');
    const appsEl = $('#worker-applications');
    if (!user.token) {
        if (statsEl) statsEl.innerHTML = emptyBlock('Sign in to see your live numbers');
        if (appsEl) appsEl.innerHTML = emptyBlock('No applications yet', 'Sign in and apply from the job feed.');
        return;
    }
    if (statsEl) statsEl.innerHTML = loadingBlock('Loading your dashboard…');
    if (appsEl) appsEl.innerHTML = loadingBlock('Loading your applications…');

    try {
        // Refresh the profile so status changes made by an admin show up without re-registering.
        const [summary, applications, profile] = await Promise.all([
            applicationsApi.workerSummary(), applicationsApi.mine(), authApi.me(),
        ]);
        if (profile) {
            session.update(profile);
            setText('#worker-dash-name', profile.name || 'Worker');
            setText('#worker-dash-city', [profile.city, profile.district].filter(Boolean).join(', ') || 'City not set');
            setText('#worker-status-kpi', profile.status);
            show('#worker-approved-only', isApproved(profile));
            show('#worker-pending-only', !isApproved(profile));
        }
        const data = summary;
        if (statsEl) {
            statsEl.innerHTML = kpiRow([
                { label: 'Available jobs', value: data.availableJobs ?? 0, tone: 'blue', hint: 'open on the platform' },
                { label: 'Applied', value: data.applied ?? 0, tone: 'amber', hint: 'waiting for a decision' },
                { label: 'Accepted', value: data.accepted ?? 0, tone: 'teal' },
                { label: 'Completed', value: data.completed ?? 0, tone: 'purple' },
                { label: 'Rejected', value: data.rejected ?? 0, tone: 'coral' },
                { label: 'Withdrawn', value: data.cancelled ?? 0, tone: 'blue' },
            ]);
        }
        if (appsEl) {
            appsEl.innerHTML = (applications || []).length
                ? applications.map(workerApplicationCard).join('')
                : emptyBlock('No applications yet', 'Open the job feed and apply - your applications are stored in PostgreSQL.');
        }
    } catch (error) {
        if (statsEl) statsEl.innerHTML = errorBlock(error.message || 'Could not load dashboard', 'lmjLoadWorkerDash');
        if (appsEl) appsEl.innerHTML = '';
    }
}

export const refreshWorkerDashboard = loadWorkerDashboard;

export async function cancelApplication(id, button) {
    await withBusyButton(button, async () => {
        try {
            await applicationsApi.cancel(id);
            toast('Application withdrawn', 'teal');
            await refreshWorkerDashboard();
        } catch (error) {
            toast(error.message || 'Could not withdraw the application', 'coral');
        }
    }, 'Working…');
}

// ------------------------------------------------------------------------- employer dashboard

export async function loadEmployerDashboard() {
    const user = currentUser();
    if (!user) return;
    setText('#employer-dash-name', user.name || 'Employer');
    setText('#employer-dash-city', [user.city, user.district].filter(Boolean).join(', ') || 'City not set');
    setText('#employer-status-kpi', user.status || 'PENDING');
    setText('#employer-city-kpi', user.city || user.district || '—');
    setText('#employer-approval-title', isApproved(user) ? 'Approved employer account' : 'Admin approval required');
    setText('#employer-approval-text', isApproved(user)
        ? 'You can post jobs and manage the applications they receive.'
        : 'Your account is PENDING. An administrator must approve it before you can post jobs.');
    show('#employer-approved-only', isApproved(user));

    const statsEl = $('#employer-stats');
    const jobsEl = $('#employer-jobs');
    const appsEl = $('#employer-applications');
    const placementsEl = $('#employer-broker-placements');
    if (!user.token || !isApproved(user)) {
        if (statsEl) statsEl.innerHTML = emptyBlock('Approval pending', 'Live counters appear once an admin approves this account.');
        if (jobsEl) jobsEl.innerHTML = '';
        if (appsEl) appsEl.innerHTML = '';
        if (placementsEl) placementsEl.innerHTML = '';
        return;
    }
    if (statsEl) statsEl.innerHTML = loadingBlock('Loading your dashboard…');
    if (jobsEl) jobsEl.innerHTML = loadingBlock('Loading your jobs…');
    if (appsEl) appsEl.innerHTML = loadingBlock('Loading applications…');
    if (placementsEl) placementsEl.innerHTML = loadingBlock('Loading broker-managed workers…');

    try {
        const [summary, jobs, applications, placements, profile] = await Promise.all([
            applicationsApi.employerSummary(), jobsApi.mine(), applicationsApi.mine(),
            jobsApi.myBrokerPlacements(), authApi.me(),
        ]);
        session.update(profile);
        setText('#employer-dash-name', profile.name || 'Employer');
        setText('#employer-dash-city', [profile.city, profile.district].filter(Boolean).join(', ') || 'City not set');
        setText('#employer-status-kpi', profile.status);
        const data = summary;
        if (statsEl) {
            statsEl.innerHTML = kpiRow([
                { label: 'My jobs', value: data.totalJobs ?? 0, tone: 'amber' },
                { label: 'Active (open)', value: data.openJobs ?? 0, tone: 'teal' },
                { label: 'Assigned', value: data.assignedJobs ?? 0, tone: 'blue' },
                { label: 'In progress', value: data.inProgressJobs ?? 0, tone: 'blue' },
                { label: 'Completed', value: data.completedJobs ?? 0, tone: 'purple' },
                { label: 'Cancelled', value: data.cancelledJobs ?? 0, tone: 'coral' },
                { label: 'Applications', value: data.totalApplications ?? 0, tone: 'teal' },
                { label: 'Pending', value: data.pendingApplications ?? 0, tone: 'amber', hint: 'need a decision' },
                { label: 'Accepted workers', value: data.acceptedWorkers ?? 0, tone: 'teal' },
            ]);
        }
        if (jobsEl) {
            jobsEl.innerHTML = (jobs || []).length
                ? jobs.map(job => `
                    <div class="app-card">
                      <div class="app-top">
                        <div>
                          <div class="app-title">${esc(job.title)}</div>
                          <div class="app-sub">${esc(job.city || '')}${job.city && job.district ? ', ' : ''}${esc(job.district || '')}
                            &middot; ${shortDate(job.jobDate)} &middot; ${esc(job.workersNeeded ?? 0)} worker(s)</div>
                        </div>
                        <div class="app-right">${jobStatusBadge(job.status)}</div>
                      </div>
                      <div class="app-foot">
                        <span class="mono app-time">${esc(job.slotsRemaining ?? 0)} slot(s) left &middot; ${esc(job.applicationCount ?? 0)} application(s)</span>
                        <span class="app-decision">
                          <button class="btn btn-secondary btn-sm" onclick="lmjViewApplications(${job.id})">Applications</button>
                          ${String(job.status).toUpperCase() === 'OPEN'
                              ? `<button class="btn btn-coral btn-sm" onclick="lmjSetJobStatus(${job.id},'CANCELLED',this)">Cancel job</button>` : ''}
                          ${String(job.status).toUpperCase() === 'ASSIGNED'
                              ? `<button class="btn btn-purple btn-sm" onclick="lmjSetJobStatus(${job.id},'IN_PROGRESS',this)">Start work</button>` : ''}
                          ${String(job.status).toUpperCase() === 'IN_PROGRESS'
                              ? `<button class="btn btn-primary btn-sm" onclick="lmjSetJobStatus(${job.id},'COMPLETED',this)">Mark completed</button>` : ''}
                        </span>
                      </div>
                    </div>`).join('')
                : emptyBlock('No jobs posted yet', 'Use "Post Job" to publish your first job to the worker feed.');
        }
        const pending = (applications || []).filter(application => String(application.status).toUpperCase() === 'APPLIED');
        const decided = (applications || []).filter(application => String(application.status).toUpperCase() !== 'APPLIED');
        if (appsEl) {
            appsEl.innerHTML = sectionTitle(`Applications needing a decision (${pending.length})`)
                + (pending.length ? pending.map(employerApplicationCard).join('')
                    : emptyBlock('No pending applications', 'New applications appear here as workers apply.'))
                + (decided.length ? sectionTitle('Earlier decisions') + decided.map(employerApplicationCard).join('') : '');
        }
        if (placementsEl) {
            placementsEl.innerHTML = placements.length ? placements.map(placement => {
                const selectedRating = Number(placement.rating || 0);
                return `<div class="app-card employer-placement-card">
                  <div class="app-top">
                    <div>
                      <div class="app-title">${esc(placement.workerName)}</div>
                      <div class="app-sub">${esc(placement.jobTitle)} &middot; ${esc([placement.city, placement.district].filter(Boolean).join(', '))}</div>
                    </div>
                    <div class="app-right"><div class="app-pay">${money(placement.payPerDay)}<span>/day</span></div>
                      <span class="badge badge-purple">Broker worker</span></div>
                  </div>
                  <div class="app-foot">
                    <span class="mono app-time">Placed ${dateTime(placement.placementDate)} &middot; ${esc(placement.brokerId)}</span>
                    <div class="placement-rating" role="group" aria-label="Rate ${esc(placement.workerName)} from 1 to 5">
                      <span>${selectedRating ? `Your rating: ${selectedRating}/5` : 'Rate worker:'}</span>
                      ${[1, 2, 3, 4, 5].map(rating => `<button type="button" class="rating-star${rating <= selectedRating ? ' selected' : ''}"
                        title="${rating} star${rating === 1 ? '' : 's'}" aria-label="Rate ${rating} out of 5"
                        onclick="lmjRateBrokerPlacement(${Number(placement.placementId)},${rating},this)">&#9733;</button>`).join('')}
                    </div>
                  </div>
                </div>`;
            }).join('') : emptyBlock('No broker-managed workers yet',
                'Workers placed by brokers on your jobs will appear here for rating.');
        }
    } catch (error) {
        if (statsEl) statsEl.innerHTML = errorBlock(error.message || 'Could not load dashboard', 'lmjLoadEmployerDash');
        if (jobsEl) jobsEl.innerHTML = '';
        if (appsEl) appsEl.innerHTML = '';
        if (placementsEl) placementsEl.innerHTML = '';
    }
}

export const refreshEmployerDashboard = loadEmployerDashboard;

export async function acceptApplication(id, button) {
    await withBusyButton(button, async () => {
        try {
            await applicationsApi.accept(id);
            toast('Applicant accepted - job slots updated', 'teal');
            await refreshEmployerDashboard();
        } catch (error) {
            toast(error.message || 'Could not accept the application', 'coral');
        }
    }, 'Accepting…');
}

export async function rejectApplication(id, button) {
    await withBusyButton(button, async () => {
        try {
            await applicationsApi.reject(id);
            toast('Applicant rejected', 'coral');
            await refreshEmployerDashboard();
        } catch (error) {
            toast(error.message || 'Could not reject the application', 'coral');
        }
    }, 'Rejecting…');
}

export async function completeApplication(id, button) {
    await withBusyButton(button, async () => {
        try {
            await applicationsApi.complete(id);
            toast('Assignment marked completed', 'purple');
            await refreshEmployerDashboard();
        } catch (error) {
            toast(error.message || 'Could not complete the assignment', 'coral');
        }
    }, 'Updating…');
}

export async function rateBrokerPlacement(placementId, rating, button) {
    await withBusyButton(button, async () => {
        try {
            await jobsApi.rateBrokerPlacement(placementId, rating);
            toast(`Worker rated ${rating} out of 5`, 'purple');
            await refreshEmployerDashboard();
        } catch (error) {
            toast(error.message || 'Could not save the rating', 'coral');
        }
    }, '…');
}

export async function setJobStatus(jobId, status, button) {
    await withBusyButton(button, async () => {
        try {
            await jobsApi.updateStatus(jobId, status);
            toast(`Job is now ${status.replace('_', ' ').toLowerCase()}`, 'teal');
            await refreshEmployerDashboard();
        } catch (error) {
            toast(error.message || 'Could not update the job', 'coral');
        }
    }, 'Updating…');
}

/** Applications of one job, shown inside the employer dashboard. */
export async function viewApplications(jobId) {
    const user = currentUser();
    if (!user?.token) {
        toast('Sign in as the employer who posted this job', 'coral');
        return navigate('employer-auth');
    }
    navigate('employer-dash');
    const appsEl = $('#employer-applications');
    if (appsEl) appsEl.innerHTML = loadingBlock('Loading applications for this job…');
    try {
        const applications = await applicationsApi.byJob(jobId);
        if (appsEl) {
            appsEl.innerHTML = sectionTitle(`Applications for job #${jobId} (${applications.length})`)
                + (applications.length
                    ? applications.map(employerApplicationCard).join('')
                    : emptyBlock('No applications for this job yet'));
        }
    } catch (error) {
        if (appsEl) appsEl.innerHTML = errorBlock(error.message || 'Could not load applications');
        toast(error.message || 'Could not load applications', 'coral');
    }
}
