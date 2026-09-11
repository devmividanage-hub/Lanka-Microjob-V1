/** Admin dashboard and reusable, database-backed KPI drill-down. */
import { authApi } from '../api/auth.js';
import { brokersApi } from '../api/brokers.js';
import { jobsApi } from '../api/jobs.js';
import { notificationsApi } from '../api/notifications.js';
import { isAdmin, session } from '../auth/session.js';
import { dataTable, kpiRow, sectionTitle } from '../components/stats.js';
import { $, emptyBlock, errorBlock, esc, loadingBlock, setHtml, setText, show, toast, withBusyButton } from '../utils/dom.js';
import { dateTime, jobStatusBadge, money, shortDate } from '../utils/format.js';

const state = {
    users: [], brokers: [], workers: [], jobs: [], notifications: [], placements: [],
    userStats: null, brokerStats: null, jobStats: null, notificationStats: null,
    refreshVersion: 0, detail: null,
};

const DETAIL_TITLES = {
    'pending-users': 'Pending Users',
    'approved-users': 'Approved Users',
    'pending-brokers': 'Pending Brokers',
    'approved-brokers': 'Approved Brokers',
    'offline-workers': 'Offline Workers',
    jobs: 'All Jobs',
    'flagged-jobs': 'Flagged Jobs',
    notifications: 'Notifications',
    placements: 'Broker Placements',
};

const present = value => value === null || value === undefined || value === '' ? 'Not available' : value;

function detailCard(title, subtitle, fields, actions = '') {
    return `<article class="admin-detail-card">
        <div class="flex-between">
            <div><div class="bac-name">${esc(present(title))}</div>
            ${subtitle ? `<div class="bac-meta">${esc(subtitle)}</div>` : ''}</div>
            ${actions ? `<div class="bac-actions">${actions}</div>` : ''}
        </div>
        <div class="admin-detail-grid">${fields.map(([label, value]) => `
            <div><span>${esc(label)}</span><strong>${esc(present(value))}</strong></div>`).join('')}</div>
    </article>`;
}

function renderUsers(users, pending) {
    if (!users.length) return emptyBlock('No records found');
    return users.map(user => detailCard(user.name, `${present(user.role)} · ${present(user.status)}`, [
        ['User ID', user.id], ['Email', user.email], ['Phone', user.mobile], ['NIC', user.nic],
        ['Role', user.role], ['District', user.district], ['City', user.city],
        ['Account status', user.status], ['Created', dateTime(user.registeredAt)],
        ['Skills', user.skills], ['Availability', user.availability],
    ], pending ? `
        <button class="btn btn-primary btn-sm" onclick="lmjApproveUser(${Number(user.id)},this)">Approve</button>
        <button class="btn btn-secondary btn-sm" onclick="lmjRejectUser(${Number(user.id)},this)">Reject</button>` : '')).join('');
}

function renderBrokers(brokers, pending) {
    if (!brokers.length) return emptyBlock('No records found');
    return brokers.map(broker => detailCard(broker.name, `${present(broker.brokerId)} · ${present(broker.status)}`, [
        ['Database ID', broker.id], ['Broker reference', broker.brokerId], ['Email', broker.email],
        ['Phone', broker.phone], ['NIC', broker.nic], ['District', broker.district], ['City', broker.city],
        ['Experience', broker.yearsExperience], ['Estimated workers', broker.estimatedWorkers],
        ['Management method', broker.workerMethod], ['Submitted', dateTime(broker.submittedAt)],
        ['Reviewed', dateTime(broker.reviewedAt)], ['Managed workers', broker.totalWorkers],
        ['Placements', broker.dashboard?.totalPlacements ?? broker.totalPlacements],
        ['Commission', money(broker.dashboard?.commissionEarned ?? broker.commissionEarned)],
        ['Active workers', broker.dashboard?.activeWorkers], ['Rating', broker.dashboard?.averageRating],
    ], pending ? `
        <button class="btn btn-primary btn-sm" onclick="lmjApproveBroker(${Number(broker.id)},this)">Approve</button>
        <button class="btn btn-secondary btn-sm" onclick="lmjRejectBroker(${Number(broker.id)},this)">Reject</button>` : '')).join('');
}

function brokerFor(worker) {
    return state.brokers.find(broker => broker.brokerId === worker.brokerId);
}

function renderWorkers(workers) {
    if (!workers.length) return emptyBlock('No records found');
    return workers.map(worker => {
        const broker = brokerFor(worker);
        return detailCard(worker.workerName, `Worker #${worker.id} · ${present(worker.status)}`, [
            ['NIC', worker.workerNic], ['Phone', worker.mobile], ['Skills', worker.skills],
            ['Availability', worker.availability], ['District', worker.district], ['City', worker.city],
            ['Broker ID', worker.brokerId], ['Broker name', broker?.name], ['Status', worker.status],
            ['Jobs completed / placements', worker.totalJobs], ['Rating', worker.rating],
            ['Commission', money(worker.commissionEarned)], ['Registered', dateTime(worker.registeredAt)],
        ]);
    }).join('');
}

function renderJobs(jobs, flaggedOnly = false) {
    if (!jobs.length) return emptyBlock('No records found');
    return jobs.map(job => detailCard(job.title, `Job #${job.id} · ${present(job.status)}`, [
        ['Employer', job.employer], ['Employer ID', job.employerId], ['Category', job.category],
        ['District', job.district], ['City', job.city], ['Pay per worker', money(job.payPerWorker)],
        ['Job date', shortDate(job.jobDate)], ['Workers needed', job.workersNeeded],
        ['Slots remaining', job.slotsRemaining], ['Applications', job.applicationCount],
        ['Status', job.status], ['Flag reason', flaggedOnly ? 'Not available' : 'Not applicable'],
        ['Created', dateTime(job.createdAt)], ['Updated', dateTime(job.updatedAt)],
    ], String(job.status).toUpperCase() === 'FLAGGED'
        ? `<button class="btn btn-secondary btn-sm" onclick="lmjFlagJob(${Number(job.id)},this,true)">Unflag</button>`
        : `<button class="btn btn-coral btn-sm" onclick="lmjFlagJob(${Number(job.id)},this)">Flag</button>`)).join('');
}

function renderNotifications(notifications) {
    if (!notifications.length) return emptyBlock('No records found');
    return notifications.map(notification => detailCard(notification.type, `Notification #${notification.id}`, [
        ['Recipient', notification.recipient], ['Channel', notification.channel], ['Message', notification.message],
        ['Delivery status', notification.status], ['Provider', notification.provider],
        ['Provider detail', notification.detail], ['Created', dateTime(notification.createdAt)],
        ['Sent', dateTime(notification.sentAt)], ['Read status', 'Not available'],
    ])).join('');
}

function renderPlacements(placements) {
    if (!placements.length) return emptyBlock('No records found');
    return placements.map(placement => detailCard(placement.jobTitle,
        `Placement #${placement.placementId} · ${present(placement.status)}`, [
            ['Broker', placement.brokerId], ['Broker database ID', placement.brokerEntityId],
            ['Worker', placement.workerName], ['Worker ID', placement.workerId],
            ['Job ID', placement.jobId], ['Employer', placement.employer],
            ['Employer ID', placement.employerId], ['Category', placement.category],
            ['Location', [placement.city, placement.district].filter(Boolean).join(', ')],
            ['Pay per day', money(placement.payPerDay)], ['Commission', money(placement.commissionAmount)],
            ['Placed', dateTime(placement.placementDate)], ['Placement status', placement.status],
            ['Job status', placement.jobStatus], ['Job slots remaining', placement.jobSlotsRemaining],
            ['Employer rating', placement.rating == null ? 'Not rated' : `${placement.rating} / 5`],
            ['Rated', dateTime(placement.ratedAt)],
        ])).join('');
}

function pendingUsers() {
    return state.users.filter(user => String(user.status).toUpperCase() === 'PENDING');
}

function approvedUsers() {
    return state.users.filter(user => String(user.status).toUpperCase() === 'APPROVED');
}

function pendingBrokers() {
    return state.brokers.filter(broker => String(broker.status).toUpperCase() === 'PENDING');
}

function approvedBrokers() {
    return state.brokers.filter(broker => String(broker.status).toUpperCase() === 'APPROVED');
}

function flaggedJobs() {
    return state.jobs.filter(job => String(job.status).toUpperCase() === 'FLAGGED');
}

function renderDashboard() {
    const statsEl = $('#admin-stats');
    if (statsEl) {
        statsEl.innerHTML = kpiRow([
            { key: 'pending-users', label: 'Pending users', value: pendingUsers().length, tone: 'amber', hint: 'click for details', action: "lmjOpenAdminDetail('pending-users')" },
            { key: 'approved-users', label: 'Approved users', value: approvedUsers().length, tone: 'teal', hint: 'click for details', action: "lmjOpenAdminDetail('approved-users')" },
            { key: 'pending-brokers', label: 'Pending brokers', value: pendingBrokers().length, tone: 'amber', hint: 'click for details', action: "lmjOpenAdminDetail('pending-brokers')" },
            { key: 'approved-brokers', label: 'Approved brokers', value: approvedBrokers().length, tone: 'purple', hint: 'click for details', action: "lmjOpenAdminDetail('approved-brokers')" },
            { key: 'offline-workers', label: 'Offline workers', value: state.workers.length, tone: 'blue', hint: 'click for details', action: "lmjOpenAdminDetail('offline-workers')" },
            { key: 'jobs', label: 'Total jobs', value: state.jobs.length, tone: 'blue', hint: 'click for details', action: "lmjOpenAdminDetail('jobs')" },
            { key: 'flagged-jobs', label: 'Flagged jobs', value: flaggedJobs().length, tone: 'coral', hint: 'click for details', action: "lmjOpenAdminDetail('flagged-jobs')" },
            { key: 'notifications', label: 'Notifications', value: state.notifications.length, tone: 'teal', hint: 'click for details', action: "lmjOpenAdminDetail('notifications')" },
            { key: 'placements', label: 'Broker placements', value: state.placements.length, tone: 'purple', hint: 'click for details', action: "lmjOpenAdminDetail('placements')" },
        ]);
    }

    const waitingUsers = pendingUsers();
    const waitingBrokers = pendingBrokers();
    setText('#admin-alert', waitingUsers.length || waitingBrokers.length
        ? `${waitingUsers.length} user(s) and ${waitingBrokers.length} broker(s) are waiting for review`
        : 'No approvals pending');
    show('#admin-alert', Boolean(waitingUsers.length || waitingBrokers.length));

    setHtml('#admin-user-queue', waitingUsers.length
        ? waitingUsers.map(user => detailCard(user.name, `${user.role} · ${user.email || user.mobile || 'no contact'}`, [
            ['NIC', user.nic], ['Location', [user.city, user.district].filter(Boolean).join(', ')],
            ['Skills', user.skills], ['Created', dateTime(user.registeredAt)],
        ], `<button class="btn btn-sm btn-primary" onclick="lmjApproveUser(${Number(user.id)},this)">Approve</button>
            <button class="btn btn-sm btn-secondary" onclick="lmjRejectUser(${Number(user.id)},this)">Reject</button>`)).join('')
        : emptyBlock('No pending users', 'New worker and employer registrations appear here.'));

    setHtml('#admin-broker-queue', waitingBrokers.length
        ? waitingBrokers.map(broker => detailCard(broker.name, broker.email || broker.phone, [
            ['NIC', broker.nic], ['District', broker.district], ['City', broker.city],
            ['Experience', broker.yearsExperience], ['Estimated workers', broker.estimatedWorkers],
        ], `<button class="btn btn-sm btn-primary" onclick="lmjApproveBroker(${Number(broker.id)},this)">Approve</button>
            <button class="btn btn-sm btn-secondary" onclick="lmjRejectBroker(${Number(broker.id)},this)">Reject</button>`)).join('')
        : emptyBlock('No pending broker applications'));

    setHtml('#admin-jobs', dataTable([
        { label: 'Job', render: job => `${esc(job.title)}<div class="cell-sub">${esc(job.employer || '—')}</div>` },
        { label: 'District', render: job => esc(job.district || '—') },
        { label: 'Pay', render: job => money(job.payPerWorker) },
        { label: 'Date', render: job => shortDate(job.jobDate) },
        { label: 'Slots', render: job => `${esc(job.slotsRemaining ?? 0)}/${esc(job.workersNeeded ?? 0)}` },
        { label: 'Status', render: job => jobStatusBadge(job.status) },
        { label: '', render: job => String(job.status).toUpperCase() === 'FLAGGED'
            ? `<button class="btn btn-secondary btn-sm" onclick="lmjFlagJob(${Number(job.id)},this,true)">Unflag</button>`
            : `<button class="btn btn-coral btn-sm" onclick="lmjFlagJob(${Number(job.id)},this)">Flag</button>` },
    ], state.jobs.slice(0, 25), 'No jobs have been posted yet.'));

    const districts = state.brokerStats?.byDistrict || [];
    setHtml('#admin-districts', districts.length
        ? `<div class="district-map">${districts.map(entry => `
            <button class="district-cell district-cell-clickable" data-district="${esc(entry.district)}"
                    onclick="lmjOpenAdminDistrict(this.dataset.district)">
              <span class="dn">${esc(entry.district)}</span>
              <span class="dv">${esc(entry.brokers)} broker(s)</span>
              <span class="dv-sub">${esc(entry.workers)} worker(s)</span>
            </button>`).join('')}</div>`
        : emptyBlock('No approved brokers yet', 'Approve a broker to see the district breakdown.'));

    const delivery = state.notificationStats;
    setHtml('#admin-notifications', delivery
        ? `<div class="mini-stats">
             <div><span>Recorded</span><strong>${esc(state.notifications.length)}</strong></div>
             <div><span>Simulated</span><strong>${esc(delivery.simulated)}</strong></div>
             <div><span>Sent for real</span><strong>${esc(delivery.sent)}</strong></div>
             <div><span>Failed</span><strong>${esc(delivery.failed)}</strong></div>
           </div><p class="sim-note">Delivery is simulated in this deployment. Records are stored in PostgreSQL.</p>`
        : emptyBlock('No notification data'));
}

export function renderAdminPanel() {
    const signedIn = isAdmin() && Boolean(session.user?.token);
    show('#admin-login-card', !signedIn);
    show('#admin-dash-content', signedIn);
    if (signedIn) refreshAdminDashboard();
}

/** Fetches every admin dataset afresh and replaces the old DOM only with one coherent response set. */
export async function refreshAdminDashboard() {
    if (!isAdmin()) return;
    const version = ++state.refreshVersion;
    setHtml('#admin-stats', loadingBlock('Loading platform statistics…'));
    ['#admin-user-queue', '#admin-broker-queue', '#admin-jobs', '#admin-districts', '#admin-notifications']
        .forEach(selector => setHtml(selector, loadingBlock()));
    try {
        const [users, brokers, workers, jobs, notifications, placements, userStats, brokerStats, jobStats, notificationStats] = await Promise.all([
            authApi.listUsers(), brokersApi.list(), brokersApi.allWorkers(), jobsApi.adminList(), notificationsApi.all(),
            brokersApi.allPlacements(),
            authApi.stats(), brokersApi.stats(), jobsApi.stats(), notificationsApi.stats(),
        ]);
        if (version !== state.refreshVersion) return;
        Object.assign(state, { users, brokers, workers, jobs, notifications, placements, userStats, brokerStats, jobStats, notificationStats });
        renderDashboard();
    } catch (error) {
        if (version !== state.refreshVersion) return;
        Object.assign(state, { users: [], brokers: [], workers: [], jobs: [], notifications: [], placements: [] });
        setHtml('#admin-stats', errorBlock(error.message || 'Unable to load data', 'lmjRefreshAdmin'));
        ['#admin-user-queue', '#admin-broker-queue', '#admin-jobs', '#admin-districts', '#admin-notifications']
            .forEach(selector => setHtml(selector, errorBlock('Unable to load data', 'lmjRefreshAdmin')));
    }
}

export const loadAdminDashboard = refreshAdminDashboard;

function updateKpi(type, count) {
    const value = $(`[data-kpi-key="${type}"] .kpi-n`);
    if (value) value.textContent = String(count);
}

function showDetail(title) {
    const modal = $('#admin-detail-modal');
    if (!modal) return false;
    modal.classList.add('open');
    modal.style.display = 'flex';
    document.body.classList.add('modal-open');
    setText('#admin-detail-title', title);
    setHtml('#admin-detail-body', loadingBlock('Loading…'));
    return true;
}

export function closeAdminDetail() {
    const modal = $('#admin-detail-modal');
    if (!modal) return;
    modal.classList.remove('open');
    modal.style.display = 'none';
    document.body.classList.remove('modal-open');
    state.detail = null;
}

export async function openAdminDetail(type) {
    if (!isAdmin() || !DETAIL_TITLES[type] || !showDetail(DETAIL_TITLES[type])) return;
    state.detail = { type };
    try {
        let records;
        if (type === 'pending-users') records = await authApi.pendingUsers();
        else if (type === 'approved-users') records = await authApi.listUsers({ status: 'APPROVED' });
        else if (type === 'pending-brokers') records = await brokersApi.pending();
        else if (type === 'approved-brokers') {
            const approved = (await brokersApi.list()).filter(broker => String(broker.status).toUpperCase() === 'APPROVED');
            records = await Promise.all(approved.map(async broker => ({
                ...broker, dashboard: broker.brokerId ? await brokersApi.dashboard(broker.brokerId) : null,
            })));
        }
        else if (type === 'offline-workers') {
            [records, state.brokers] = await Promise.all([brokersApi.allWorkers(), brokersApi.list()]);
        } else if (type === 'jobs' || type === 'flagged-jobs') {
            const allJobs = await jobsApi.adminList();
            records = type === 'flagged-jobs' ? allJobs.filter(job => String(job.status).toUpperCase() === 'FLAGGED') : allJobs;
        } else if (type === 'placements') records = await brokersApi.allPlacements();
        else records = await notificationsApi.all();

        if (state.detail?.type !== type) return;
        updateKpi(type, records.length);
        const html = type.endsWith('users') ? renderUsers(records, type === 'pending-users')
            : type.endsWith('brokers') ? renderBrokers(records, type === 'pending-brokers')
                : type === 'offline-workers' ? renderWorkers(records)
                    : type === 'jobs' ? renderJobs(records)
                        : type === 'flagged-jobs' ? renderJobs(records, true)
                            : type === 'placements' ? renderPlacements(records) : renderNotifications(records);
        setHtml('#admin-detail-body', html);
    } catch (error) {
        if (state.detail?.type === type) setHtml('#admin-detail-body', errorBlock(error.message || 'Unable to load data', 'lmjRetryAdminDetail'));
    }
}

export async function openAdminDistrict(district) {
    if (!isAdmin() || !showDetail(`${district} District`)) return;
    state.detail = { type: 'district', district };
    try {
        const [brokers, workers] = await Promise.all([brokersApi.list(), brokersApi.allWorkers()]);
        if (state.detail?.type !== 'district' || state.detail.district !== district) return;
        state.brokers = brokers;
        const districtBrokers = brokers.filter(broker => String(broker.status).toUpperCase() === 'APPROVED'
            && String(broker.district).toLowerCase() === String(district).toLowerCase());
        const detailedBrokers = await Promise.all(districtBrokers.map(async broker => ({
            ...broker, dashboard: broker.brokerId ? await brokersApi.dashboard(broker.brokerId) : null,
        })));
        const brokerIds = new Set(districtBrokers.map(broker => broker.brokerId));
        const districtWorkers = workers.filter(worker => brokerIds.has(worker.brokerId));
        setHtml('#admin-detail-body', sectionTitle(`Brokers (${districtBrokers.length})`)
            + renderBrokers(detailedBrokers, false)
            + sectionTitle(`Offline workers (${districtWorkers.length})`)
            + renderWorkers(districtWorkers));
    } catch (error) {
        setHtml('#admin-detail-body', errorBlock(error.message || 'Unable to load data', 'lmjRetryAdminDetail'));
    }
}

export function retryAdminDetail() {
    const detail = state.detail;
    if (!detail) return;
    if (detail.type === 'district') openAdminDistrict(detail.district);
    else openAdminDetail(detail.type);
}

async function refreshAfterMutation() {
    const detail = state.detail ? { ...state.detail } : null;
    await refreshAdminDashboard();
    if (detail?.type === 'district') await openAdminDistrict(detail.district);
    else if (detail?.type) await openAdminDetail(detail.type);
}

export async function approveUser(id, button) {
    await withBusyButton(button, async () => {
        try { await authApi.approveUser(id); toast('User approved - they can now log in', 'teal'); await refreshAfterMutation(); }
        catch (error) { toast(error.message || 'Could not approve the user', 'coral'); }
    }, 'Approving…');
}

export async function rejectUser(id, button) {
    await withBusyButton(button, async () => {
        try { await authApi.rejectUser(id); toast('User rejected', 'coral'); await refreshAfterMutation(); }
        catch (error) { toast(error.message || 'Could not reject the user', 'coral'); }
    }, 'Rejecting…');
}

export async function approveBroker(id, button) {
    await withBusyButton(button, async () => {
        try { const broker = await brokersApi.approve(id); toast(`Broker approved - reference ${broker.brokerId}`, 'purple'); await refreshAfterMutation(); }
        catch (error) { toast(error.message || 'Could not approve the broker', 'coral'); }
    }, 'Approving…');
}

export async function rejectBroker(id, button) {
    await withBusyButton(button, async () => {
        try { await brokersApi.reject(id); toast('Broker application rejected', 'coral'); await refreshAfterMutation(); }
        catch (error) { toast(error.message || 'Could not reject the broker', 'coral'); }
    }, 'Rejecting…');
}

export async function flagJob(id, button, unflag = false) {
    await withBusyButton(button, async () => {
        try {
            if (unflag) await jobsApi.updateStatus(id, 'OPEN');
            else await jobsApi.flag(id);
            toast(unflag ? 'Job returned to the feed' : 'Job flagged and hidden from the feed', 'amber');
            await refreshAfterMutation();
        } catch (error) { toast(error.message || 'Could not update the job', 'coral'); }
    }, 'Updating…');
}
