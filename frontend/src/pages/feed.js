/**
 * The worker job feed.
 *
 * Jobs come from GET /jobs (job-service -> PostgreSQL) and are enriched with real match scores from
 * POST /matches/batch (matching-service). "Applied" state comes from GET /applications/mine, so it
 * survives a page reload instead of living in a JavaScript object.
 */
import { applicationsApi } from '../api/applications.js';
import { jobsApi } from '../api/jobs.js';
import { currentUser, isApproved, isWorker } from '../auth/session.js';
import { jobCardList } from '../components/jobCard.js';
import { loadFeedWithMatches } from '../services/feedService.js';
import { $, $$, emptyBlock, errorBlock, esc, loadingBlock, setHtml, show, toast, withBusyButton } from '../utils/dom.js';
import { jobStatusBadge, money, shortDate, skillsList } from '../utils/format.js';
import { citiesOf, districts } from '../utils/locations.js';

const state = {
    district: '', city: '', category: '', search: '', jobs: [], matches: new Map(), applied: new Map(),
    requestVersion: 0,
};
let searchTimer = null;

export const feedState = state;

function canApply() {
    const user = currentUser();
    return isWorker() && Boolean(user?.token) && isApproved(user);
}

async function loadApplied() {
    state.applied = new Map();
    if (!canApply()) return;
    try {
        const applications = await applicationsApi.mine();
        (applications || []).forEach(application => {
            if (['APPLIED', 'ACCEPTED', 'COMPLETED'].includes(String(application.status).toUpperCase())) {
                state.applied.set(application.jobId, application.status);
            }
        });
    } catch {
        // The feed still renders; the apply button will report the real error if it is clicked.
    }
}

function render() {
    const list = $('#job-list');
    if (!list) return;
    const summary = $('#feed-summary');
    if (summary) {
        const count = state.jobs.length;
        const location = [state.city, state.district].filter(Boolean).join(', ');
        summary.textContent = `${count} open job${count === 1 ? '' : 's'} available${location ? ` in ${location}` : ' across all districts'}`;
    }
    if (!state.jobs.length) {
        list.innerHTML = emptyBlock('No jobs match your current filters.',
            'Try changing the location, category, or search term.');
        return;
    }
    list.innerHTML = jobCardList(state.jobs, state.matches, {
        canApply: canApply(),
        applicationStatusFor: jobId => state.applied.get(jobId),
    });
}

export async function loadFeed() {
    const list = $('#job-list');
    if (!list) return;
    const user = currentUser();
    // A signed-in worker sees their own district first; visitors can filter freely.
    if (!state.district && user?.district) state.district = user.district;
    syncFilterUi();

    const version = ++state.requestVersion;
    state.jobs = [];
    state.matches = new Map();
    const summary = $('#feed-summary');
    if (summary) summary.textContent = 'Loading nearby jobs…';
    list.innerHTML = loadingBlock('Loading nearby jobs…');
    try {
        const [{ jobs, matches }] = await Promise.all([
            loadFeedWithMatches({
                district: state.district, city: state.city,
                category: state.category, search: state.search,
            }),
            loadApplied(),
        ]);
        if (version !== state.requestVersion) return;
        state.jobs = jobs;
        state.matches = matches;
        render();
    } catch (error) {
        if (version !== state.requestVersion) return;
        state.jobs = [];
        state.matches = new Map();
        if (summary) summary.textContent = 'Jobs are currently unavailable';
        list.innerHTML = errorBlock('Unable to load jobs. Please try again.', 'lmjReloadFeed');
        toast('Could not load the job feed', 'coral');
    }
}

function updateCityOptions() {
    const citySelect = $('#feed-city');
    if (!citySelect) return;
    const availableCities = state.district ? citiesOf(state.district) : [];
    citySelect.innerHTML = '<option value="">All cities</option>' + availableCities
        .map(city => `<option value="${esc(city)}">${esc(city)}</option>`).join('');
    citySelect.disabled = !availableCities.length;
    citySelect.value = availableCities.includes(state.city) ? state.city : '';
}

function syncFilterUi() {
    const districtSelect = $('#feed-district');
    if (districtSelect) districtSelect.value = state.district || '';
    updateCityOptions();
    const categorySelect = $('#feed-category');
    if (categorySelect) categorySelect.value = state.category || '';
    const search = $('#feed-search');
    if (search && search.value !== state.search) search.value = state.search;
    $$('#page-feed .filter-chip').forEach(chip => {
        chip.classList.toggle('on', (chip.dataset.district || '') === state.district);
    });
}

export function onFeedDistrictChange(value) {
    state.district = value || '';
    state.city = '';
    syncFilterUi();
    loadFeed();
}

export function onFeedCityChange(value) {
    state.city = value || '';
    loadFeed();
}

export function onFeedCategoryChange(value) {
    state.category = value === 'all' ? '' : value;
    loadFeed();
}

export function onFeedSearch(value) {
    state.search = value || '';
    clearTimeout(searchTimer);
    searchTimer = setTimeout(loadFeed, 250);
}

export function filterDistrictChip(chip, district) {
    state.district = district === 'all' ? '' : district;
    state.city = '';
    syncFilterUi();
    loadFeed();
}

export function resetFeedFilters() {
    clearTimeout(searchTimer);
    state.district = '';
    state.city = '';
    state.category = '';
    state.search = '';
    syncFilterUi();
    loadFeed();
}

/** Apply for a job: POST /applications, then refresh so the pill reflects the database. */
export async function applyToJob(jobId, button) {
    if (!canApply()) {
        toast('Sign in as an approved worker to apply', 'coral');
        return;
    }
    await withBusyButton(button, async () => {
        try {
            await applicationsApi.apply(jobId);
            toast('Application submitted and saved', 'teal');
            await loadApplied();
            render();
        } catch (error) {
            toast(error.message || 'Could not apply', 'coral');
        }
    }, 'Applying…');
}

/** Job details modal, built from GET /jobs/{id}. */
export async function openJobDetail(jobId) {
    const overlay = $('#job-detail-modal');
    const body = $('#job-detail-body');
    if (!overlay || !body) return;
    overlay.classList.add('open');
    overlay.style.display = 'flex';
    document.body.classList.add('modal-open');
    body.innerHTML = loadingBlock('Loading job details…');
    try {
        const job = await jobsApi.get(jobId);
        const skills = skillsList(job.requiredSkills);
        const match = state.matches.get(job.id);
        const applicationStatus = state.applied.get(job.id);
        const open = String(job.status).toUpperCase() === 'OPEN';
        const available = Number(job.slotsRemaining ?? 0) > 0;
        const location = [job.city, job.district].filter(Boolean).join(', ') || 'Location not specified';
        body.innerHTML = `
            <div class="job-detail-head">
                <div><h3>${esc(job.title)}</h3>
                <p class="msub">${esc(job.employer || 'Employer')} &middot; ${esc(location)}</p></div>
                <div class="job-detail-pay"><strong>${money(job.payPerWorker).replace(/^Rs\./, 'Rs. ')}</strong><span>per day</span></div>
            </div>
            <div class="job-detail-badges"><span class="tag tag-blue">${esc(job.category || 'General')}</span>${jobStatusBadge(job.status)}</div>
            <div class="detail-grid job-detail-grid">
                <div><span>Workers needed</span><strong>${esc(job.workersNeeded ?? 0)}</strong></div>
                <div><span>Slots remaining</span><strong>${esc(job.slotsRemaining ?? 0)}</strong></div>
                <div><span>Job date</span><strong>${shortDate(job.jobDate)}</strong></div>
                <div><span>District</span><strong>${esc(job.district || '—')}</strong></div>
                <div><span>City</span><strong>${esc(job.city || '—')}</strong></div>
                <div><span>Required skills</span><strong>${esc(skills.join(', ') || 'No specific skills')}</strong></div>
            </div>
            ${match ? `<div class="detail-match"><span>Match score</span><strong>${match.score}% &middot; ${esc(match.recommendation || 'Match')}</strong><small>Rule-based skill match</small></div>` : ''}
            <div class="job-detail-description"><span>Job description</span><p>${esc(job.additionalNotes || 'No additional description was provided.')}</p></div>
            <div class="detail-actions">
                <button class="btn btn-secondary" onclick="lmjCloseJobDetail()">Close</button>
                ${applicationStatus ? `<span class="applied-pill">${String(applicationStatus).toUpperCase() === 'ACCEPTED' ? 'Accepted' : 'Applied'}</span>`
                    : canApply() && open && available
                        ? `<button class="btn btn-primary" onclick="lmjApplyFromDetail(${job.id}, this)">Apply Now</button>`
                        : `<button class="btn btn-primary" disabled>${available && open ? 'Apply Now' : available ? 'Not Open' : 'Job Full'}</button>`}
            </div>`;
    } catch (error) {
        body.innerHTML = errorBlock(error.message || 'Could not load this job');
    }
}

export function closeJobDetail() {
    const overlay = $('#job-detail-modal');
    if (!overlay) return;
    overlay.classList.remove('open');
    overlay.style.display = 'none';
    document.body.classList.remove('modal-open');
}

export async function applyFromDetail(jobId, button) {
    await applyToJob(jobId, button);
    closeJobDetail();
}

/** Populates the feed's district/city/category controls once, on first render. */
export function initFeedControls() {
    const districtSelect = $('#feed-district');
    if (districtSelect && districtSelect.options.length <= 1) {
        districtSelect.innerHTML = '<option value="">All districts</option>' + districts()
            .map(district => `<option value="${esc(district)}">${esc(district)}</option>`).join('');
    }
    const chips = $('#feed-district-chips');
    if (chips && !chips.children.length) {
        const popular = ['Colombo', 'Gampaha', 'Kandy', 'Galle', 'Kalutara'];
        chips.innerHTML = `<button type="button" class="filter-chip on" data-district="" onclick="lmjFilterDistrictChip(this,'')">All Districts</button>`
            + popular.map(district =>
                `<button type="button" class="filter-chip" data-district="${esc(district)}" onclick="lmjFilterDistrictChip(this,'${esc(district)}')">${esc(district)}</button>`).join('');
    }
    show('#feed-guest-note', !canApply());
    syncFilterUi();
}
