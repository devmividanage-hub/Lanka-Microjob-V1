import { api, query } from './client.js';

/** user-service (/auth) - registration, login, admin approval and the user directory. */
export const authApi = {
    register: (payload) => api.post('/auth/register', payload),
    login: (payload) => api.post('/auth/login', payload),
    adminLogin: (gmail, password) => api.post('/auth/admin/login', { gmail, password }),
    me: () => api.get('/auth/me'),
    pendingUsers: () => api.get('/auth/users/pending'),
    listUsers: (params) => api.get(`/auth/users${query(params)}`),
    approveUser: (id) => api.put(`/auth/users/${id}/approve`),
    rejectUser: (id) => api.put(`/auth/users/${id}/reject`),
    stats: () => api.get('/auth/stats'),
    publicStats: () => api.get('/auth/public-stats'),
};
