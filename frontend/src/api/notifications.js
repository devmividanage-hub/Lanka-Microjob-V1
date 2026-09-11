import { api } from './client.js';

/** notification-service (/notifications) - the log of events raised for the signed-in user. */
export const notificationsApi = {
    mine: () => api.get('/notifications/mine'),
    forRecipient: (recipient) => api.get(`/notifications/recipient/${encodeURIComponent(recipient)}`),
    all: () => api.get('/notifications'),
    stats: () => api.get('/notifications/stats'),
    providers: () => api.get('/notifications/providers'),
};
