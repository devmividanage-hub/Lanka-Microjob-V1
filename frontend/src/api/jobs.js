import { api, query } from './client.js';

/** job-service (/jobs) - the real job feed and the employer's own jobs. */
export const jobsApi = {
    list: (params) => api.get(`/jobs${query(params)}`),
    adminList: () => api.get('/jobs/admin'),
    get: (id) => api.get(`/jobs/${id}`),
    create: (payload) => api.post('/jobs', payload),
    mine: (status) => api.get(`/jobs/mine${query({ status })}`),
    myBrokerPlacements: () => api.get('/jobs/placements/mine'),
    rateBrokerPlacement: (placementId, rating) =>
        api.put(`/jobs/placements/${placementId}/rating`, { rating }),
    byEmployer: (employerId) => api.get(`/jobs/employer/${employerId}`),
    stats: () => api.get('/jobs/stats'),
    publicStats: () => api.get('/jobs/public-stats'),
    updateStatus: (id, status) => api.put(`/jobs/${id}/status${query({ status })}`),
    flag: (id) => api.put(`/jobs/${id}/flag`),
};
