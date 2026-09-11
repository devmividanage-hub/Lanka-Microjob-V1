/** Notification drawer: the real log of events the backend recorded for the signed-in user. */
import { notificationsApi } from '../api/notifications.js';
import { currentBroker, currentUser } from '../auth/session.js';
import { $, emptyBlock, errorBlock, esc, loadingBlock, setText, toast } from '../utils/dom.js';
import { dateTime } from '../utils/format.js';

export function toggleNotifications(forceOpen) {
    const drawer = $('#notification-drawer');
    if (!drawer) return;
    const open = forceOpen === undefined ? !drawer.classList.contains('open') : forceOpen;
    drawer.classList.toggle('open', open);
    if (open) loadNotifications();
}

export async function loadNotifications() {
    const list = $('#notification-list');
    if (!list) return;
    if (!currentUser()?.token && !currentBroker()?.token) {
        list.innerHTML = emptyBlock('Sign in to see your notifications');
        return;
    }
    list.innerHTML = loadingBlock('Loading notifications…');
    try {
        const [notifications, providers] = await Promise.all([
            notificationsApi.mine(),
            notificationsApi.providers().catch(() => null),
        ]);
        setText('#notification-count', `${notifications.length}`);
        const providerNote = providers
            ? Object.entries(providers).map(([channel, name]) => `${esc(channel)}: ${esc(name)}`).join(' · ')
            : '';
        list.innerHTML = (notifications.length
            ? notifications.map(item => `
                <div class="note-item note-${esc(String(item.status || '').toLowerCase())}">
                  <div class="note-top">
                    <span class="note-type">${esc(String(item.type || 'GENERAL').replaceAll('_', ' '))}</span>
                    <span class="note-status">${esc(item.status)}</span>
                  </div>
                  <div class="note-message">${esc(item.message)}</div>
                  <div class="note-meta mono">${esc(item.channel)} &middot; ${dateTime(item.createdAt)}</div>
                </div>`).join('')
            : emptyBlock('No notifications yet', 'Events such as approval, application and acceptance decisions appear here.'))
            + (providerNote
                ? `<p class="sim-note">Providers in this deployment - ${providerNote}. SIMULATED means the message was stored and logged, not sent.</p>`
                : '');
    } catch (error) {
        list.innerHTML = errorBlock(error.message || 'Could not load notifications', 'lmjLoadNotifications');
    }
}
