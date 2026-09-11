/**
 * Single HTTP layer for the whole UI.
 *
 * Every page talks to the backend through this module, so authentication headers, error shaping and
 * 401 handling exist in exactly one place. In docker/Kubernetes the frontend is served by nginx which
 * reverse-proxies the API prefixes to the gateway, so requests are same-origin (no CORS). In Vite dev
 * mode `vite.config.js` proxies the same prefixes to http://localhost:9000.
 */

export class ApiError extends Error {
    constructor(status, message, fieldErrors, payload) {
        super(message || `Request failed (${status})`);
        this.name = 'ApiError';
        this.status = status;
        this.fieldErrors = fieldErrors || null;
        this.payload = payload || null;
    }

    get isUnauthorized() {
        return this.status === 401;
    }

    get isForbidden() {
        return this.status === 403;
    }

    /** First field error, useful for inline form messages. */
    get firstFieldError() {
        if (!this.fieldErrors) return null;
        const [field] = Object.keys(this.fieldErrors);
        return field ? `${field}: ${this.fieldErrors[field]}` : null;
    }
}

function resolveBaseUrl() {
    const configured = import.meta.env && import.meta.env.VITE_API_BASE;
    if (configured) return String(configured).replace(/\/+$/, '');
    if (typeof window === 'undefined') return '';
    if (window.location.protocol === 'file:') return 'http://localhost:9000';
    // Same origin: nginx (docker/k8s) or the Vite dev proxy handles the API prefixes.
    return '';
}

export const API_BASE = resolveBaseUrl();

const unauthorizedHandlers = [];

/** Register a callback invoked on any 401 so the session layer can sign the user out. */
export function onUnauthorized(handler) {
    unauthorizedHandlers.push(handler);
}

let tokenProvider = () => null;

/** The session module injects the current JWT here (avoids a circular import). */
export function setTokenProvider(provider) {
    tokenProvider = provider;
}

async function parse(res) {
    const text = await res.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch {
            data = null;
        }
    }
    if (!res.ok) {
        const message = (data && (data.message || data.error)) || text || `Request failed (${res.status})`;
        const error = new ApiError(res.status, message, data && data.fieldErrors, data);
        if (res.status === 401) unauthorizedHandlers.forEach(handler => handler(error));
        throw error;
    }
    return data;
}

function headers(extra = {}) {
    const token = tokenProvider();
    const result = { Accept: 'application/json', ...extra };
    if (token) result.Authorization = `Bearer ${token}`;
    return result;
}

async function send(method, path, body, options = {}) {
    const init = { method, headers: headers(body === undefined ? {} : { 'Content-Type': 'application/json' }) };
    if (body !== undefined) init.body = JSON.stringify(body);
    let res;
    try {
        res = await fetch(API_BASE + path, init);
    } catch (networkError) {
        throw new ApiError(0, options.offlineMessage
            || 'Cannot reach the API gateway. Is the backend running?', null, null);
    }
    return parse(res);
}

export const api = {
    get: (path, options) => send('GET', path, undefined, options),
    post: (path, body, options) => send('POST', path, body ?? {}, options),
    put: (path, body, options) => send('PUT', path, body, options),
    del: (path, options) => send('DELETE', path, undefined, options),
};

/** Builds a query string, dropping empty values so filters stay optional. */
export function query(params = {}) {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') search.append(key, value);
    });
    const encoded = search.toString();
    return encoded ? `?${encoded}` : '';
}
