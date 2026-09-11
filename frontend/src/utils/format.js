/** Display formatting. Kept separate so every page formats money and dates the same way. */

export const money = (amount) => {
    const numeric = Number(amount ?? 0);
    return `Rs.${numeric.toLocaleString('en-LK')}`;
};

export const shortDate = (value) => {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
};

export const dateTime = (value) => {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
};

export const todayIso = () => new Date().toISOString().slice(0, 10);

const JOB_STATUS_LABELS = {
    OPEN: 'Open', ASSIGNED: 'Assigned', IN_PROGRESS: 'In progress', COMPLETED: 'Completed',
    CANCELLED: 'Cancelled', EXPIRED: 'Expired', FLAGGED: 'Flagged', COMPLETE: 'Completed',
    FILLED: 'Filled', CLOSED: 'Closed'
};

const APPLICATION_STATUS_LABELS = {
    APPLIED: 'Pending', ACCEPTED: 'Accepted', REJECTED: 'Rejected',
    CANCELLED: 'Withdrawn', COMPLETED: 'Completed'
};

export const jobStatusLabel = (status) => JOB_STATUS_LABELS[String(status).toUpperCase()] || status || '—';
export const applicationStatusLabel = (status) => APPLICATION_STATUS_LABELS[String(status).toUpperCase()] || status || '—';

/** Maps a status onto the existing badge colour classes so the visual identity is preserved. */
export function jobStatusBadge(status) {
    const key = String(status || '').toUpperCase();
    const tone = { OPEN: 'teal', ASSIGNED: 'blue', IN_PROGRESS: 'blue', COMPLETED: 'purple', CANCELLED: 'coral',
        EXPIRED: 'amber', FLAGGED: 'coral', FILLED: 'muted', CLOSED: 'coral' }[key] || 'amber';
    return `<span class="badge badge-${tone}">${jobStatusLabel(key)}</span>`;
}

export function applicationStatusBadge(status) {
    const key = String(status || '').toUpperCase();
    const tone = { APPLIED: 'amber', ACCEPTED: 'teal', REJECTED: 'coral', CANCELLED: 'blue', COMPLETED: 'purple' }[key] || 'amber';
    return `<span class="badge badge-${tone}">${applicationStatusLabel(key)}</span>`;
}

export const skillsList = (value) => String(value || '')
    .split(',')
    .map(skill => skill.trim())
    .filter(Boolean);

/** Match score colour, matching the labels the matching-service returns. */
export function matchTone(score) {
    if (score >= 70) return 'teal';
    if (score >= 40) return 'blue';
    return 'amber';
}
