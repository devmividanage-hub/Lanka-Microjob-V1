/**
 * Client-side router for the single-page UI.
 *
 * The markup in index.html declares one <div class="page" id="page-..."> per view; this module
 * switches them, enforces the role/approval gates on the client (the backend enforces them for
 * real) and runs the data-loading hook of the page being entered.
 */
import { $, $$, toast } from '../utils/dom.js';
import { currentUser, currentBroker, isAdmin, isApproved, isBrokerSignedIn, isEmployer, isWorker, session } from '../auth/session.js';

export const PAGES = ['home', 'worker-auth', 'employer-auth', 'broker-register', 'post-job', 'feed',
    'worker-dash', 'employer-dash', 'broker-dash', 'admin'];

const enterHooks = {};

/** Registers what should happen (usually: load real data) when a page becomes visible. */
export function onPageEnter(page, hook) {
    enterHooks[page] = hook;
}

export function currentPage() {
    return $$('.page.active').map(page => page.id.replace('page-', ''))[0] || 'home';
}

function markNav(page) {
    $$('.nav-tabs .nav-tab, .mobile-menu .nav-tab').forEach(tab => {
        tab.classList.toggle('active', tab.dataset.page === page);
    });
}

/** Raw switch without guards - used internally after a guard decides where to go. */
function showPage(page) {
    $$('.page').forEach(candidate => candidate.classList.toggle('active', candidate.id === `page-${page}`));
    markNav(page);
    document.getElementById('mobile-menu')?.classList.remove('open');
    document.getElementById('hamburger')?.classList.remove('open');
    if (window.location.hash !== `#${page}`) {
        window.history.replaceState(null, '', `#${page}`);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
    const hook = enterHooks[page];
    if (hook) {
        try {
            const result = hook();
            if (result && typeof result.catch === 'function') result.catch(() => {});
        } catch (error) {
            console.error(`Page hook for ${page} failed`, error);
        }
    }
}

/**
 * Navigate with role gates.
 * Rules mirror the backend: the feed and dashboards need an approved account, posting a job needs an
 * approved employer, the broker dashboard needs an approved broker, the admin panel needs ADMIN.
 */
export function navigate(page, options = {}) {
    const user = currentUser();
    const silent = options.silent === true;

    if (page === 'feed') {
        if (!isWorker()) {
            if (!silent) toast('Sign in as a worker to browse the job feed', 'coral');
            return showPage(user && user.role === 'WORKER' ? 'worker-dash' : 'worker-auth');
        }
        if (!user.token) {
            if (!silent) toast('Sign in to load your personalised feed', 'coral');
            return showPage('worker-auth');
        }
        if (!isApproved(user)) {
            if (!silent) toast('Admin approval is required before applying to jobs', 'coral');
            return showPage('worker-dash');
        }
    }

    if (page === 'post-job') {
        if (!isEmployer()) {
            if (!silent) toast('Sign in as an employer to post a job', 'coral');
            return showPage('employer-auth');
        }
        if (!user.token) {
            if (!silent) toast('Sign in to post a job', 'coral');
            return showPage('employer-auth');
        }
        if (!isApproved(user)) {
            if (!silent) toast('Admin approval is required before posting jobs', 'coral');
            return showPage('employer-dash');
        }
    }

    if (page === 'worker-dash' && !user) return showPage('worker-auth');
    if (page === 'employer-dash' && !user) return showPage('employer-auth');

    if (page === 'broker-dash' && !isBrokerSignedIn() && !isAdmin()) {
        return showPage('broker-dash');
    }

    return showPage(page);
}

/** Toggles the mobile navigation drawer. */
export function toggleMobileMenu() {
    const menu = $('#mobile-menu');
    const burger = $('#hamburger');
    if (!menu) return;
    const open = menu.classList.toggle('open');
    burger?.classList.toggle('open', open);
}

/** Keeps the "who am I" state of the nav in sync with the session. */
export function syncSessionUi() {
    const user = session.user;
    const broker = currentBroker();
    $$('.nav-tab[data-auth-page]').forEach(tab => {
        tab.style.display = user || broker ? 'none' : '';
    });
}
