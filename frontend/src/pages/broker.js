/**
 * Broker flow: application -> admin review -> login -> dashboard -> offline workers -> placements.
 * Everything is persisted by broker-service; the dashboard shows only real database values.
 */
import { brokersApi } from '../api/brokers.js';
import { matchesApi } from '../api/matches.js';
import { currentBroker, session } from '../auth/session.js';
import { kpiRow, sectionTitle } from '../components/stats.js';
import { $, clearSkills, emptyBlock, errorBlock, esc, loadingBlock, selectedSkills, setHtml, setText, show, toast, value, withBusyButton } from '../utils/dom.js';
import { money, shortDate, skillsList } from '../utils/format.js';
import { cascadeCity } from '../utils/locations.js';

let brokerStep = 1;
let brokerRefreshVersion = 0;
let latestBrokerDashboard = null;
let latestBrokerWorkers = [];
let placementJobs = [];
let placementMatches = new Map();
let selectedPlacementJobId = null;
let placementLoadVersion = 0;

export function brokerNextStep(step) {
    if (step === 2) {
        if (!value('#br-name') || !value('#br-nic') || !value('#br-email') || !$('#br-password')?.value) {
            return toast('Fill name, NIC, email and password', 'coral');
        }
    }
    if (step === 3 && !value('#br-district')) {
        return toast('Select the district you will be locked to', 'coral');
    }
    brokerStep = step;
    [1, 2, 3].forEach(index => {
        show(`#bstep${index}`, index === step);
        const bar = $(`#bs${index}`);
        if (bar) bar.className = `bstep${index < step ? ' done' : index === step ? ' active' : ''}`;
    });
    return undefined;
}

export function onBrokerDistrictChange() {
    cascadeCity('br-district', 'br-city-field', 'br-city', updateCityBadge);
    updateCityBadge();
}

export function updateCityBadge() {
    const district = value('#br-district');
    const city = value('#br-city');
    const wrap = $('#city-badge-wrap');
    if (!wrap) return;
    wrap.innerHTML = district
        ? `<div class="city-badge">Locked to ${esc(city ? `${city}, ${district} District` : `${district} District`)} - your operational area</div>`
        : '';
}

export async function submitBrokerApplication(button) {
    if (!$('#broker-agree')?.checked) {
        return toast('Accept the broker code of conduct to continue', 'coral');
    }
    const payload = {
        name: value('#br-name'),
        nic: value('#br-nic'),
        phone: value('#br-phone'),
        email: value('#br-email'),
        password: $('#br-password')?.value || '',
        district: value('#br-district'),
        city: value('#br-city'),
        yearsExperience: value('#br-exp'),
        estimatedWorkers: value('#br-est'),
        workerMethod: value('#br-method'),
        idProof: value('#br-idproof'),
        agreedToCodeOfConduct: true,
    };
    const errorEl = $('#broker-form-error');
    const fail = (message) => {
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';
        }
        toast(message, 'coral');
    };
    if (!payload.name || !payload.nic || !payload.email || !payload.password || !payload.district) {
        return fail('Name, NIC, email, password and district are required');
    }
    if (errorEl) errorEl.style.display = 'none';

    return withBusyButton(button, async () => {
        try {
            const broker = await brokersApi.apply(payload);
            show('#broker-pending-card', true);
            show('#broker-pending', true);
            setText('#broker-pending-ref', `Reference #${broker.id} - status ${broker.status}`);
            toast('Application submitted. An admin must approve it before you can log in.', 'purple');
        } catch (error) {
            fail(error.firstFieldError ? `${error.message}: ${error.firstFieldError}` : (error.message || 'Broker application failed'));
        }
    }, 'Submitting…');
}

/** Broker dashboard entry point: shows the login card until a broker session exists. */
export function renderBrokerDashboard() {
    const broker = currentBroker();
    show('#broker-login-card', !broker?.token);
    show('#broker-dash-content', Boolean(broker?.token));
    if (!broker?.token) return;
    setText('#broker-dash-name', 'Loading…');
    setText('#broker-dash-ref', '—');
    setText('#broker-city-lock', 'City-locked: Loading…');
    setText('#broker-city-lock-hint', '');
    refreshBrokerDashboard();
}

/**
 * Reads the worker collection and dashboard summary afresh. The version guard prevents an older,
 * slower request from overwriting a newer refresh after a mutation.
 */
export async function refreshBrokerDashboard() {
    const broker = currentBroker();
    if (!broker?.brokerId) return;
    const refreshVersion = ++brokerRefreshVersion;
    const statsEl = $('#broker-stats');
    const tableEl = $('#broker-worker-tbody');
    if (statsEl) statsEl.innerHTML = loadingBlock('Loading your dashboard…');
    if (tableEl) tableEl.innerHTML = `<tr><td colspan="8">${loadingBlock('Loading workers…')}</td></tr>`;
    try {
        // Keep these as separate authoritative reads: workers first, then the repository-derived KPIs.
        const workers = await brokersApi.workers(broker.brokerId);
        const data = await brokersApi.dashboard(broker.brokerId);
        if (refreshVersion !== brokerRefreshVersion) return;
        latestBrokerDashboard = data;
        latestBrokerWorkers = workers;
        setText('#broker-dash-name', data.name || '—');
        setText('#broker-dash-ref', data.brokerId || '—');
        setText('#broker-city-lock', `City-locked: ${data.city || data.district || '—'}`);
        setText('#broker-city-lock-hint',
            `Offline workers you register are locked to ${data.district || 'your'} District.`);
        const rateEl = $('#placement-rate');
        if (rateEl) {
            rateEl.dataset.rate = String(data.commissionRate ?? 0);
            rateEl.textContent = data.commissionRate == null
                ? 'rate not available'
                : `${Math.round(data.commissionRate * 1000) / 10}%`;
        }
        if (statsEl) {
            statsEl.innerHTML = kpiRow([
                { label: 'Managed workers', value: workers.length, tone: 'blue' },
                { label: 'Active now', value: data.activeWorkers ?? 0, tone: 'teal' },
                { label: 'On a job', value: data.onJobWorkers ?? 0, tone: 'amber' },
                { label: 'Placements', value: data.totalPlacements ?? 0, tone: 'purple' },
                {
                    label: 'Commission earned',
                    value: money(data.commissionEarned ?? 0),
                    tone: 'amber',
                    hint: data.commissionRate == null
                        ? 'commission rate not available'
                        : `${Math.round(data.commissionRate * 1000) / 10}% per placement`,
                },
                {
                    label: 'Avg worker rating',
                    value: data.averageRating == null ? 'Not rated' : `${data.averageRating}`,
                    tone: 'purple',
                    hint: data.averageRating == null ? 'no ratings recorded yet' : 'from recorded ratings',
                },
            ]);
        }
        setText('#broker-dash-district', data.district || '');
        setText('#broker-worker-count', `${workers.length} worker(s)`);
        if (tableEl) {
            tableEl.innerHTML = workers.length
                ? workers.map(worker => `
                    <tr data-worker-id="${worker.id}">
                      <td>${esc(worker.workerName)}<div class="cell-sub">${esc(worker.mobile || 'no mobile')}</div></td>
                      <td>${esc(worker.skills || '—')}</td>
                      <td>${esc(worker.city || worker.district || '—')}</td>
                      <td><span class="badge badge-${worker.status === 'ACTIVE' ? 'teal' : worker.status === 'ON_JOB' ? 'amber' : 'blue'}">${esc(worker.status)}</span></td>
                      <td>${esc(worker.totalJobs ?? 0)}</td>
                      <td>${worker.rating == null ? '<span class="muted">not rated</span>' : `${esc(worker.rating)}&#9733;`}</td>
                      <td>${money(worker.commissionEarned ?? 0)}</td>
                      <td class="cell-actions">
                        <select onchange="lmjSetWorkerStatus(${worker.id}, this.value)" aria-label="Worker status">
                          ${['ACTIVE', 'ON_JOB', 'INACTIVE'].map(status => `<option value="${status}"${worker.status === status ? ' selected' : ''}>${status.replace('_', ' ')}</option>`).join('')}
                        </select>
                        <button class="btn btn-purple btn-sm" data-worker-name="${esc(worker.workerName)}"
                          onclick="lmjOpenPlacement(${worker.id}, this)"${worker.status !== 'ACTIVE' ? ' disabled title="Only ACTIVE workers can be placed"' : ''}>Placement</button>
                      </td>
                    </tr>`).join('')
                : `<tr><td colspan="8">${emptyBlock('No offline workers yet', 'Register a worker who cannot use the app directly.')}</td></tr>`;
        }
    } catch (error) {
        if (refreshVersion !== brokerRefreshVersion) return;
        latestBrokerDashboard = null;
        latestBrokerWorkers = [];
        if (statsEl) statsEl.innerHTML = errorBlock(error.message || 'Could not load the dashboard', 'lmjLoadBrokerDash');
        if (tableEl) tableEl.innerHTML = `<tr><td colspan="8">${errorBlock(error.message || 'Could not load workers', 'lmjLoadBrokerDash')}</td></tr>`;
    }
}

// Backwards-compatible name used by existing retry buttons and markup.
export const loadBrokerDashboard = refreshBrokerDashboard;

export function toggleAddWorker(forceOpen) {
    const modal = $('#add-worker-modal');
    if (!modal) return;
    const broker = currentBroker();
    const open = forceOpen === undefined ? !modal.classList.contains('open') : forceOpen;
    modal.classList.toggle('open', open);
    modal.style.display = open ? 'flex' : 'none';
    document.body.classList.toggle('modal-open', open);
    if (open) {
        const profile = latestBrokerDashboard;
        setText('#aw-district-lock', profile
            ? `${profile.city || ''}${profile.city && profile.district ? ', ' : ''}${profile.district || ''} District`
            : 'Loading assigned area…');
        setText('#aw-broker-ref', profile?.brokerId || broker?.brokerId || '—');
        setText('#aw-broker-contact', profile?.phone || profile?.email || '—');
    }
}

export async function addOfflineWorker(button) {
    const broker = currentBroker();
    if (!broker?.brokerId) {
        toast('Sign in as an approved broker first', 'coral');
        return;
    }
    const payload = {
        workerName: value('#aw-name'),
        workerNic: value('#aw-nic'),
        mobile: value('#aw-mobile'),
        skills: selectedSkills('#add-worker-modal .skills-grid').join(','),
        availability: value('#aw-availability'),
    };
    const errorEl = $('#add-worker-error');
    const fail = (message) => {
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';
        }
        toast(message, 'coral');
    };
    if (!payload.workerName) return fail('Worker name is required');
    if (!payload.mobile) return fail('Mobile number is required');
    if (!payload.skills) return fail('Select at least one skill');
    if (errorEl) errorEl.style.display = 'none';

    return withBusyButton(button, async () => {
        try {
            const worker = await brokersApi.addWorker(broker.brokerId, payload);
            toast(`${worker.workerName} registered and saved under ${broker.brokerId}`, 'purple');
            ['#aw-name', '#aw-nic', '#aw-mobile'].forEach(selector => {
                const el = $(selector);
                if (el) el.value = '';
            });
            clearSkills('#add-worker-modal .skills-grid');
            toggleAddWorker(false);
            await refreshBrokerDashboard();
        } catch (error) {
            fail(error.firstFieldError ? `${error.message}: ${error.firstFieldError}` : (error.message || 'Could not register the worker'));
        }
    }, 'Registering…');
}

export async function setWorkerStatus(workerId, status) {
    const broker = currentBroker();
    if (!broker?.brokerId) return;
    try {
        await brokersApi.setWorkerStatus(broker.brokerId, workerId, status);
        toast(`Worker marked ${status.replace('_', ' ').toLowerCase()}`, 'teal');
        await refreshBrokerDashboard();
    } catch (error) {
        toast(error.message || 'Could not update the worker', 'coral');
    }
}

export async function openPlacementModal(workerId, workerName) {
    const modal = $('#placement-modal');
    if (!modal) return;
    const broker = currentBroker();
    const worker = latestBrokerWorkers.find(item => Number(item.id) === Number(workerId));
    if (!broker?.brokerId || !worker) return toast('Refresh the dashboard and try again', 'coral');
    const loadVersion = ++placementLoadVersion;
    modal.dataset.workerId = String(workerId);
    setText('#placement-worker-name', worker.workerName || workerName || '—');
    setText('#placement-worker-area', [worker.city, worker.district].filter(Boolean).join(', ') || '—');
    setText('#placement-worker-skills', worker.skills || '—');
    placementJobs = [];
    placementMatches = new Map();
    selectedPlacementJobId = null;
    const search = $('#placement-job-search');
    if (search) search.value = '';
    setHtml('#placement-job-list', loadingBlock('Loading available jobs…'));
    setHtml('#placement-selected-job', emptyBlock('Select a job', 'Choose one eligible job to review its details.'));
    setText('#placement-commission-preview', '—');
    const confirm = $('#placement-confirm-button');
    if (confirm) confirm.disabled = true;
    modal.classList.add('open');
    modal.style.display = 'flex';
    document.body.classList.add('modal-open');
    try {
        placementJobs = await brokersApi.eligibleJobs(broker.brokerId, workerId);
        if (loadVersion !== placementLoadVersion || Number(modal.dataset.workerId) !== Number(workerId)) return;
        const workerSkills = skillsList(worker.skills);
        if (placementJobs.length && workerSkills.length) {
            try {
                const response = await matchesApi.scoreBatch({
                    workerDistrict: worker.district || null,
                    workerSkills,
                    jobs: placementJobs.map(job => ({
                        jobId: job.jobId,
                        district: job.district || null,
                        requiredSkills: skillsList(job.requiredSkills),
                    })),
                });
                if (loadVersion !== placementLoadVersion) return;
                placementMatches = new Map((response?.results || []).map(match => [Number(match.jobId), match]));
                placementJobs.sort((a, b) => (placementMatches.get(Number(b.jobId))?.score ?? 0)
                    - (placementMatches.get(Number(a.jobId))?.score ?? 0));
            } catch {
                // Eligibility is authoritative in job-service; scores are a display enhancement.
            }
        }
        renderPlacementJobs();
    } catch (error) {
        if (loadVersion === placementLoadVersion) {
            setHtml('#placement-job-list', errorBlock(error.message || 'Unable to load available jobs', 'lmjRetryPlacementJobs'));
        }
    }
}

export function closePlacementModal() {
    const modal = $('#placement-modal');
    if (!modal) return;
    modal.classList.remove('open');
    modal.style.display = 'none';
    document.body.classList.remove('modal-open');
    placementLoadVersion += 1;
    placementJobs = [];
    placementMatches = new Map();
    selectedPlacementJobId = null;
}

function renderPlacementJobs() {
    const query = value('#placement-job-search').toLowerCase();
    const visible = placementJobs.filter(job => !query || [job.title, job.employer, job.category,
        job.district, job.city, job.requiredSkills].some(field => String(field || '').toLowerCase().includes(query)));
    if (!placementJobs.length) {
        return setHtml('#placement-job-list', emptyBlock('No suitable open jobs are currently available for this worker.'));
    }
    if (!visible.length) return setHtml('#placement-job-list', emptyBlock('No eligible jobs match that search.'));
    setHtml('#placement-job-list', visible.map(job => {
        const match = placementMatches.get(Number(job.jobId));
        const selected = Number(job.jobId) === selectedPlacementJobId;
        return `<button type="button" class="placement-job-card${selected ? ' selected' : ''}"
            onclick="lmjSelectPlacementJob(${Number(job.jobId)})" aria-pressed="${selected}">
          <span class="placement-radio" aria-hidden="true"></span>
          <span class="placement-job-copy">
            <strong>${esc(job.title)}</strong>
            <small>${esc(job.category || 'General')} · ${esc([job.city, job.district].filter(Boolean).join(', '))}</small>
            <small>${esc(job.requiredSkills || 'No specific skill')} · ${esc(job.slotsRemaining)} slot(s) left</small>
          </span>
          <span class="placement-job-side"><strong>${money(job.payPerWorker)}</strong><small>per day</small>
            ${match ? `<span class="badge badge-teal">${esc(match.score)}% match</span>` : ''}</span>
        </button>`;
    }).join(''));
}

export function filterPlacementJobs() {
    renderPlacementJobs();
}

export function selectPlacementJob(jobId) {
    const job = placementJobs.find(item => Number(item.jobId) === Number(jobId));
    if (!job) return;
    selectedPlacementJobId = Number(jobId);
    renderPlacementJobs();
    const match = placementMatches.get(Number(job.jobId));
    const rate = Number($('#placement-rate')?.dataset?.rate || latestBrokerDashboard?.commissionRate || 0);
    const commission = rate > 0 ? Math.round(Number(job.payPerWorker || 0) * rate) : null;
    setHtml('#placement-selected-job', `<div class="placement-detail-grid">
      <div><span>Job ID</span><strong>#${esc(job.jobId)}</strong></div>
      <div><span>Employer</span><strong>${esc(job.employer || '—')}</strong></div>
      <div><span>Category</span><strong>${esc(job.category || '—')}</strong></div>
      <div><span>Location</span><strong>${esc([job.city, job.district].filter(Boolean).join(', ') || '—')}</strong></div>
      <div><span>Pay</span><strong>${money(job.payPerWorker)} / day</strong></div>
      <div><span>Job date</span><strong>${shortDate(job.jobDate)}</strong></div>
      <div><span>Available slots</span><strong>${esc(job.slotsRemaining)}</strong></div>
      <div><span>Required skills</span><strong>${esc(job.requiredSkills || 'No specific skill')}</strong></div>
      ${match ? `<div><span>Match</span><strong>${esc(match.score)}% · ${esc(match.recommendation)}</strong></div>` : ''}
    </div>`);
    setText('#placement-commission-preview', commission == null ? 'Calculated by backend' : `${money(commission)} estimated`);
    const confirm = $('#placement-confirm-button');
    if (confirm) confirm.disabled = false;
}

export function retryPlacementJobs() {
    const modal = $('#placement-modal');
    const workerId = Number(modal?.dataset?.workerId || 0);
    const worker = latestBrokerWorkers.find(item => Number(item.id) === workerId);
    if (worker) openPlacementModal(workerId, worker.workerName);
}

export async function recordPlacement(button) {
    const broker = currentBroker();
    const modal = $('#placement-modal');
    const workerId = Number(modal?.dataset?.workerId || 0);
    if (!broker?.brokerId || !workerId) return;
    if (!selectedPlacementJobId) return toast('Select an available job first', 'coral');
    const payload = { jobId: selectedPlacementJobId };
    return withBusyButton(button, async () => {
        try {
            const result = await brokersApi.recordPlacement(broker.brokerId, workerId, payload);
            toast(`${result.worker.workerName} placed on ${result.placement.jobTitle} — commission ${money(result.commissionForPlacement)}`, 'purple');
            closePlacementModal();
            await refreshBrokerDashboard();
        } catch (error) {
            toast(error.message || 'Could not record the placement', 'coral');
        }
    }, 'Placing…');
}

export function brokerLogout() {
    session.clearBroker();
    toast('Broker signed out', 'purple');
    renderBrokerDashboard();
}
