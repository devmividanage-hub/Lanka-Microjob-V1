/**
 * Session handling for the two identities the platform has:
 *   - a platform user (WORKER / EMPLOYER / ADMIN) authenticated by user-service
 *   - a broker authenticated by broker-service
 *
 * Both are kept in localStorage so a page reload keeps you signed in, and both carry the JWT that
 * the API layer attaches to every request. When the backend answers 401 (expired or revoked token)
 * the session is dropped and the UI is sent back to the right login screen.
 */
import { onUnauthorized, setTokenProvider } from '../api/client.js';

const USER_KEY = 'lmj_user';
const BROKER_KEY = 'lmj_broker';

const lostHandlers = [];

function read(key) {
    try {
        return JSON.parse(localStorage.getItem(key) || 'null');
    } catch {
        return null;
    }
}

function write(key, value) {
    if (value === null || value === undefined) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
}

/** Called by main.js so a dropped session can navigate without creating an import cycle. */
export function onSessionLost(handler) {
    lostHandlers.push(handler);
}

function notifyLost(reason) {
    lostHandlers.forEach(handler => {
        try {
            handler(reason);
        } catch {
            /* a failing handler must not block sign-out */
        }
    });
}

export const session = {
    get user() {
        return read(USER_KEY);
    },
    set(user) {
        write(USER_KEY, user);
    },
    update(patch) {
        const current = read(USER_KEY);
        if (!current) return null;
        const merged = { ...current, ...patch };
        write(USER_KEY, merged);
        return merged;
    },
    clear() {
        write(USER_KEY, null);
    },
    get broker() {
        return read(BROKER_KEY);
    },
    setBroker(broker) {
        write(BROKER_KEY, broker);
    },
    clearBroker() {
        write(BROKER_KEY, null);
    },
};

setTokenProvider(() => session.user?.token || session.broker?.token || null);

onUnauthorized(() => {
    const hadUser = Boolean(session.user);
    session.clear();
    session.clearBroker();
    notifyLost(hadUser ? 'user' : 'broker');
});

export const currentUser = () => session.user;
export const currentBroker = () => session.broker;

export const isSignedIn = () => Boolean(session.user?.token);
export const isApproved = (user = session.user) => String(user?.status || '').toUpperCase() === 'APPROVED';
export const hasRole = (...roles) => roles.includes(String(session.user?.role || '').toUpperCase());
export const isAdmin = () => hasRole('ADMIN');
export const isWorker = () => hasRole('WORKER');
export const isEmployer = () => hasRole('EMPLOYER');
export const isBrokerSignedIn = () => Boolean(session.broker?.token);

/** Decodes the JWT expiry (without verifying it) purely to warn the user before a call fails. */
export function tokenExpiresAt(token) {
    if (!token) return null;
    try {
        const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const decoded = JSON.parse(decodeURIComponent(escape(atob(payload))));
        return decoded.exp ? new Date(decoded.exp * 1000) : null;
    } catch {
        return null;
    }
}

export function sessionExpiresInMinutes() {
    const expiry = tokenExpiresAt(session.user?.token || session.broker?.token);
    if (!expiry) return null;
    return Math.max(0, Math.round((expiry.getTime() - Date.now()) / 60000));
}

/** Clears both identities. Called by the Logout button in the nav. */
export function logout(reason = 'user') {
    session.clear();
    session.clearBroker();
    notifyLost(reason);
}

/** Builds the user object stored after a successful /auth/login. */
export function userFromLogin(email, data) {
    return {
        token: data.token,
        id: data.id,
        role: data.role,
        name: data.name,
        email: data.email || email,
        mobile: data.mobile || null,
        nic: data.nic || null,
        skills: data.skills || '',
        availability: data.availability || '',
        status: data.status,
        district: data.district || '',
        city: data.city || '',
    };
}
