/**
 * Application bootstrap.
 *
 * The markup lives in index.html and calls the `lmj*` functions registered here, so this module is
 * the single place where the UI is wired to the page modules. Everything data-related goes through
 * src/api/* against the API gateway - there is no hardcoded job, broker or dashboard data left.
 */
import { onUnauthorized } from './api/client.js';
import { session, onSessionLost, logout, currentUser, currentBroker } from './auth/session.js';
import { citiesOf, cascadeCity, populateDistrictSelects } from './utils/locations.js';
import { $, toast } from './utils/dom.js';
import { navigate, onPageEnter, toggleMobileMenu, PAGES, currentPage } from './services/router.js';

import * as authPage from './pages/auth.js';
import * as feedPage from './pages/feed.js';
import * as dashboards from './pages/dashboards.js';
import * as postJobPage from './pages/postJob.js';
import * as brokerPage from './pages/broker.js';
import * as adminPage from './pages/admin.js';
import * as notificationsPage from './pages/notifications.js';
import * as homePage from './pages/home.js';

// ---------------------------------------------------------------- page enter hooks (real data)
onPageEnter('home', () => {
    authPage.updateAuthUi();
    return homePage.loadHomeStats();
});
onPageEnter('worker-auth', () => authPage.updateAuthUi());
onPageEnter('employer-auth', () => authPage.updateAuthUi());
onPageEnter('broker-register', () => authPage.updateAuthUi());
onPageEnter('feed', () => {
    feedPage.initFeedControls();
    return feedPage.loadFeed();
});
onPageEnter('worker-dash', () => dashboards.refreshWorkerDashboard());
onPageEnter('employer-dash', () => dashboards.refreshEmployerDashboard());
onPageEnter('post-job', () => postJobPage.initPostJobForm());
onPageEnter('broker-dash', () => brokerPage.renderBrokerDashboard());
onPageEnter('admin', () => adminPage.renderAdminPanel());

// Keep the page modules decoupled: auth.js only needs to re-render these two panels after login.
authPage.bindAdminPanelRenderer(adminPage.renderAdminPanel);
authPage.bindBrokerDashboardRenderer(brokerPage.renderBrokerDashboard);

// A 401 anywhere means the token is gone: drop the session and return to the right login screen.
onSessionLost((kind) => {
    authPage.updateAuthUi();
    if (kind === 'broker') {
        brokerPage.renderBrokerDashboard();
        if (currentPage() === 'broker-dash') return;
    }
    const page = currentPage();
    if (['feed', 'worker-dash', 'employer-dash', 'post-job', 'admin', 'broker-dash'].includes(page)) {
        navigate(page, { silent: true });
    }
});

// ----------------------------------------------------------------------- global UI helpers
function goto(page) {
    navigate(page);
}

function toggleSkill(chip) {
    chip.classList.toggle('on');
    if (chip.closest('#page-post-job')) postJobPage.updateJobPreview();
}

function toggleSkillAmber(chip) {
    chip.classList.toggle('on');
    const on = chip.classList.contains('on');
    chip.style.background = on ? 'var(--amber-bg)' : '';
    chip.style.borderColor = on ? 'var(--amber)' : '';
    chip.style.color = on ? 'var(--amber)' : '';
    if (chip.closest('#page-post-job')) postJobPage.updateJobPreview();
}

// Handlers referenced from index.html and from the rendered components.
const handlers = {
    // navigation & session
    lmjGoto: goto,
    lmjMobileGoto: goto,
    lmjToggleMenu: toggleMobileMenu,
    lmjLogout: () => {
        logout();
        adminPage.renderAdminPanel();
        brokerPage.renderBrokerDashboard();
        authPage.updateAuthUi();
    },
    lmjSessionExpires: () => toast(`Session expires in ${Math.round((session.user?.token ? 1 : 1))} minutes`, 'amber'),

    // worker / employer / admin / broker authentication
    lmjWorkerTab: authPage.switchWorkerTab,
    lmjEmployerTab: authPage.switchEmployerTab,
    lmjWorkerLogin: (event) => authPage.workerLogin(event?.currentTarget || event?.target),
    lmjWorkerRegister: (event) => authPage.workerRegister(event?.currentTarget || event?.target),
    lmjEmployerLogin: (event) => authPage.employerLogin(event?.currentTarget || event?.target),
    lmjEmployerRegister: (event) => authPage.employerRegister(event?.currentTarget || event?.target),
    lmjAdminLogin: (event) => authPage.adminLogin(event?.currentTarget || event?.target),
    lmjBrokerLogin: (event) => authPage.brokerLogin(event?.currentTarget || event?.target),

    // job feed
    lmjReloadFeed: () => feedPage.loadFeed(),
    lmjFeedDistrict: feedPage.onFeedDistrictChange,
    lmjFeedCity: feedPage.onFeedCityChange,
    lmjFeedCategory: feedPage.onFeedCategoryChange,
    lmjFeedSearch: feedPage.onFeedSearch,
    lmjFilterDistrictChip: (chip, district) => feedPage.filterDistrictChip(chip, district),
    lmjResetFilters: feedPage.resetFeedFilters,
    lmjApply: (jobId, button) => feedPage.applyToJob(jobId, button),
    lmjJobDetail: (jobId) => feedPage.openJobDetail(jobId),
    lmjCloseJobDetail: feedPage.closeJobDetail,
    lmjApplyFromDetail: (jobId, button) => feedPage.applyFromDetail(jobId, button),
    lmjCitiesOf: citiesOf,

    // worker dashboard
    lmjLoadWorkerDash: () => dashboards.refreshWorkerDashboard(),
    lmjCancelApplication: (id, button) => dashboards.cancelApplication(id, button),

    // employer dashboard + job lifecycle
    lmjLoadEmployerDash: () => dashboards.refreshEmployerDashboard(),
    lmjAcceptApplication: (id, button) => dashboards.acceptApplication(id, button),
    lmjRejectApplication: (id, button) => dashboards.rejectApplication(id, button),
    lmjCompleteApplication: (id, button) => dashboards.completeApplication(id, button),
    lmjRateBrokerPlacement: (placementId, rating, button) =>
        dashboards.rateBrokerPlacement(placementId, rating, button),
    lmjViewApplications: (jobId) => dashboards.viewApplications(jobId),
    lmjSetJobStatus: (jobId, status, button) => dashboards.setJobStatus(jobId, status, button),

    // posting a job
    lmjPostJobDistrict: postJobPage.onPostJobDistrictChange,
    lmjUpdatePreview: postJobPage.updateJobPreview,
    lmjShowPreview: () => {
        postJobPage.updateJobPreview();
        const preview = $('#pj-preview');
        if (preview) preview.style.display = 'block';
    },
    lmjSubmitJob: (event) => postJobPage.submitJob(event?.currentTarget || event?.target),
    lmjResetJobForm: postJobPage.resetPostJobForm,

    // broker flow
    lmjBrokerNext: brokerPage.brokerNextStep,
    lmjBrokerDistrict: brokerPage.onBrokerDistrictChange,
    lmjUpdateCityBadge: brokerPage.updateCityBadge,
    lmjBrokerSubmit: (event) => brokerPage.submitBrokerApplication(event?.currentTarget || event?.target),
    lmjLoadBrokerDash: () => brokerPage.loadBrokerDashboard(),
    lmjToggleAddWorker: brokerPage.toggleAddWorker,
    lmjAddWorker: (event) => brokerPage.addOfflineWorker(event?.currentTarget || event?.target),
    lmjSetWorkerStatus: brokerPage.setWorkerStatus,
    lmjOpenPlacement: (workerId, button) =>
        brokerPage.openPlacementModal(workerId, button?.dataset?.workerName || ''),
    lmjClosePlacement: brokerPage.closePlacementModal,
    lmjFilterPlacementJobs: brokerPage.filterPlacementJobs,
    lmjSelectPlacementJob: brokerPage.selectPlacementJob,
    lmjRetryPlacementJobs: brokerPage.retryPlacementJobs,
    lmjRecordPlacement: (event) => brokerPage.recordPlacement(event?.currentTarget || event?.target),
    lmjBrokerLogout: brokerPage.brokerLogout,

    // admin panel
    lmjLoadAdminDash: () => adminPage.loadAdminDashboard(),
    lmjRefreshAdmin: () => adminPage.refreshAdminDashboard(),
    lmjOpenAdminDetail: adminPage.openAdminDetail,
    lmjOpenAdminDistrict: adminPage.openAdminDistrict,
    lmjCloseAdminDetail: adminPage.closeAdminDetail,
    lmjRetryAdminDetail: adminPage.retryAdminDetail,
    lmjApproveUser: (id, button) => adminPage.approveUser(id, button),
    lmjRejectUser: (id, button) => adminPage.rejectUser(id, button),
    lmjApproveBroker: (id, button) => adminPage.approveBroker(id, button),
    lmjRejectBroker: (id, button) => adminPage.rejectBroker(id, button),
    lmjFlagJob: (id, button, unflag) => adminPage.flagJob(id, button, unflag),

    // notifications
    lmjToggleNotifications: notificationsPage.toggleNotifications,
    lmjLoadNotifications: notificationsPage.loadNotifications,

    // shared form helpers
    lmjToggleSkill: toggleSkill,
    lmjToggleSkillAmber: toggleSkillAmber,
    lmjCascadeCity: cascadeCity,
};

Object.entries(handlers).forEach(([name, fn]) => {
    window[name] = fn;
});

// ----------------------------------------------------------------------------- bootstrap
function boot() {
    populateDistrictSelects(['wr-district', 'er-district', 'br-district', 'pj-dist']);
    feedPage.initFeedControls();
    postJobPage.initPostJobForm();
    authPage.updateAuthUi();
    adminPage.renderAdminPanel();
    brokerPage.renderBrokerDashboard();
    homePage.loadHomeStats();

    // Deep link support: #feed, #admin, ... restores the right view after a reload.
    const hash = (window.location.hash || '').replace('#', '');
    const user = currentUser();
    const broker = currentBroker();
    if (hash && PAGES.includes(hash)) {
        navigate(hash, { silent: true });
    } else if (broker?.token) {
        navigate('broker-dash', { silent: true });
    } else if (user?.token) {
        navigate(user.role === 'ADMIN' ? 'admin' : user.role === 'EMPLOYER' ? 'employer-dash' : 'feed', { silent: true });
    } else if (user) {
        navigate(user.role === 'EMPLOYER' ? 'employer-dash' : 'worker-dash', { silent: true });
    }

    // Warn before a token silently expires mid-session.
    window.addEventListener('online', () => toast('Back online', 'teal'));
    window.addEventListener('offline', () => toast('You are offline - requests will fail until the connection returns', 'coral'));
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
} else {
    boot();
}
