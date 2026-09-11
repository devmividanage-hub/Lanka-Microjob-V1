/** Worker / employer / admin authentication against user-service. */
import { authApi } from '../api/auth.js';
import { brokersApi } from '../api/brokers.js';
import { session, userFromLogin } from '../auth/session.js';
import { $, esc, show, toast, value, withBusyButton, selectedSkills } from '../utils/dom.js';
import { navigate } from '../services/router.js';

const fieldError = (selector, message) => {
    const el = $(selector);
    if (el) {
        el.textContent = message || '';
        el.style.display = message ? 'block' : 'none';
    }
};

function describe(error) {
    if (!error) return 'Request failed';
    return error.firstFieldError ? `${error.message} (${error.firstFieldError})` : error.message;
}

export function switchWorkerTab(tab, button) {
    document.querySelectorAll('#page-worker-auth .tab-btn').forEach(btn => btn.classList.remove('active'));
    button?.classList.add('active');
    show('#worker-login', tab === 'login');
    show('#worker-register', tab === 'register');
    fieldError('#worker-auth-error', '');
}

export function switchEmployerTab(tab, button) {
    document.querySelectorAll('#page-employer-auth .tab-btn').forEach(btn => btn.classList.remove('active'));
    button?.classList.add('active');
    show('#employer-login', tab === 'login');
    show('#employer-register', tab === 'register');
    fieldError('#employer-auth-error', '');
}

export async function workerLogin(button) {
    const email = value('#wl-email');
    const password = $('#wl-password')?.value || '';
    if (!email || !password) {
        fieldError('#worker-auth-error', 'Enter your email and password');
        return;
    }
    fieldError('#worker-auth-error', '');
    await withBusyButton(button, async () => {
        try {
            const data = await authApi.login({ email, password, role: 'WORKER' });
            session.clearBroker();
            session.set(userFromLogin(email, data));
            show('#success-worker', false);
            toast(`Welcome back, ${data.name || 'worker'}`, 'teal');
            navigate('worker-dash');
        } catch (error) {
            fieldError('#worker-auth-error', describe(error));
            toast(error.status === 403 ? 'Admin approval is still pending' : 'Worker login failed', 'coral');
        }
    }, 'Signing in…');
}

export async function workerRegister(button) {
    const payload = {
        name: value('#wr-name'),
        mobile: value('#wr-phone'),
        email: value('#wr-email'),
        password: $('#wr-password')?.value || '',
        role: 'WORKER',
        district: value('#wr-district'),
        city: value('#wr-city'),
        nic: value('#wr-nic'),
        skills: selectedSkills('#worker-register .skills-grid').join(','),
        availability: value('#wr-availability'),
    };
    if (!payload.name || !payload.email || !payload.password) {
        fieldError('#worker-auth-error', 'Name, email and password are required');
        return;
    }
    if (!payload.skills) {
        fieldError('#worker-auth-error', 'Select at least one skill so jobs can be matched to you');
        return;
    }
    fieldError('#worker-auth-error', '');
    await withBusyButton(button, async () => {
        try {
            const data = await authApi.register(payload);
            // Registered but not approved yet: store the profile without a token so the dashboard can
            // show the real PENDING status and the login form is one click away.
            session.set({
                id: data.id, role: 'WORKER', name: payload.name, email: payload.email,
                mobile: payload.mobile, nic: payload.nic, skills: payload.skills,
                availability: payload.availability, status: data.status,
                district: payload.district, city: payload.city, token: null,
            });
            show('#success-worker', true);
            toast('Registered. Waiting for admin approval.', 'teal');
            setTimeout(() => navigate('worker-dash'), 900);
        } catch (error) {
            fieldError('#worker-auth-error', describe(error));
            toast('Worker registration failed', 'coral');
        }
    }, 'Registering…');
}

export async function employerLogin(button) {
    const email = value('#el-email');
    const password = $('#el-password')?.value || '';
    if (!email || !password) {
        fieldError('#employer-auth-error', 'Enter your email and password');
        return;
    }
    fieldError('#employer-auth-error', '');
    await withBusyButton(button, async () => {
        try {
            const data = await authApi.login({ email, password, role: 'EMPLOYER' });
            session.clearBroker();
            session.set(userFromLogin(email, data));
            toast(`Welcome back, ${data.name || 'employer'}`, 'amber');
            navigate('employer-dash');
        } catch (error) {
            fieldError('#employer-auth-error', describe(error));
            toast(error.status === 403 ? 'Admin approval is still pending' : 'Employer login failed', 'coral');
        }
    }, 'Signing in…');
}

export async function employerRegister(button) {
    const business = value('#er-biz');
    const payload = {
        name: value('#er-name') || business,
        mobile: value('#er-phone'),
        email: value('#er-email'),
        password: $('#er-password')?.value || '',
        role: 'EMPLOYER',
        district: value('#er-district'),
        city: value('#er-city'),
    };
    if (!business || !payload.email || !payload.password) {
        fieldError('#employer-auth-error', 'Business name, email and password are required');
        return;
    }
    fieldError('#employer-auth-error', '');
    await withBusyButton(button, async () => {
        try {
            const data = await authApi.register(payload);
            session.set({
                id: data.id, role: 'EMPLOYER', name: payload.name, email: payload.email,
                mobile: payload.mobile, status: data.status, district: payload.district,
                city: payload.city, token: null,
            });
            show('#success-employer', true);
            toast('Registered. Waiting for admin approval.', 'amber');
            setTimeout(() => navigate('employer-dash'), 900);
        } catch (error) {
            fieldError('#employer-auth-error', describe(error));
            toast('Employer registration failed', 'coral');
        }
    }, 'Registering…');
}

export async function adminLogin(button) {
    const gmail = value('#admin-email');
    const password = $('#admin-password')?.value || '';
    if (!gmail || !password) {
        fieldError('#admin-auth-error', 'Enter the admin email and password');
        return;
    }
    fieldError('#admin-auth-error', '');
    await withBusyButton(button, async () => {
        try {
            const data = await authApi.adminLogin(gmail, password);
            session.clearBroker();
            session.set({ token: data.token, id: data.id, role: 'ADMIN', name: data.name, email: gmail, status: 'APPROVED' });
            toast('Admin signed in', 'blue');
            renderAdminPanel();
            navigate('admin');
        } catch (error) {
            fieldError('#admin-auth-error', describe(error));
            toast('Admin login failed', 'coral');
        }
    }, 'Signing in…');
}

export async function brokerLogin(button) {
    const email = value('#broker-login-email');
    const password = $('#broker-login-password')?.value || '';
    if (!email || !password) {
        fieldError('#broker-auth-error', 'Enter your broker email and password');
        return;
    }
    fieldError('#broker-auth-error', '');
    await withBusyButton(button, async () => {
        try {
            const data = await brokersApi.login({ email, password });
            session.clear();
            session.setBroker(data);
            toast(`Broker ${data.brokerId} signed in`, 'purple');
            renderBrokerDashboard();
            navigate('broker-dash');
        } catch (error) {
            fieldError('#broker-auth-error', describe(error));
            toast(error.status === 403 ? 'Broker application is not approved yet' : 'Broker login failed', 'coral');
        }
    }, 'Signing in…');
}

export function logout() {
    session.clear();
    session.clearBroker();
    toast('Signed out', 'teal');
    navigate('home');
    updateAuthUi();
}

/**
 * The nav shows a real, signed-in state: role chip + logout, or the login tabs when signed out.
 * Called after every navigation and session change.
 */
export function updateAuthUi() {
    const user = session.user;
    const broker = session.broker;
    const chip = $('#session-chip');
    if (chip) {
        if (user?.token) {
            chip.style.display = 'flex';
            chip.innerHTML = `<span class="session-role role-${esc(String(user.role).toLowerCase())}">${esc(user.role)}</span>
                <span class="session-name">${esc(user.name || user.email || '')}</span>
                <button class="btn btn-secondary btn-sm" onclick="lmjLogout()">Logout</button>`;
        } else if (broker?.token) {
            chip.style.display = 'flex';
            chip.innerHTML = `<span class="session-role role-broker">BROKER</span>
                <span class="session-name">${esc(broker.brokerId || broker.name || '')}</span>
                <button class="btn btn-secondary btn-sm" onclick="lmjLogout()">Logout</button>`;
        } else if (user) {
            chip.style.display = 'flex';
            chip.innerHTML = `<span class="session-role role-${esc(String(user.role).toLowerCase())}">${esc(user.role)}</span>
                <span class="session-name">${esc(user.name || user.email || '')} &middot; ${esc(user.status || 'PENDING')}</span>
                <button class="btn btn-secondary btn-sm" onclick="lmjLogout()">Clear</button>`;
        } else {
            chip.style.display = 'none';
            chip.innerHTML = '';
        }
    }
    // Registration banners are only relevant while signed out.
    if (user?.token) {
        show('#success-worker', false);
        show('#success-employer', false);
    }
}

// Lazy imports resolved at call time keep the page modules independent of each other.
let renderAdminPanel = () => {};
let renderBrokerDashboard = () => {};

export function bindAdminPanelRenderer(fn) {
    renderAdminPanel = fn;
}

export function bindBrokerDashboardRenderer(fn) {
    renderBrokerDashboard = fn;
}
