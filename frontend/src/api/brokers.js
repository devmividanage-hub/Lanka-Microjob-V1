import { api } from './client.js';

/** broker-service (/brokers) - applications, admin review, offline workers and placements. */
export const brokersApi = {
    apply: (payload) => api.post('/brokers', payload),
    login: (payload) => api.post('/brokers/login', payload),
    list: () => api.get('/brokers'),
    allWorkers: () => api.get('/brokers/workers'),
    pending: () => api.get('/brokers/pending'),
    stats: () => api.get('/brokers/stats'),
    publicStats: () => api.get('/brokers/public-stats'),
    approve: (id) => api.put(`/brokers/${id}/approve`),
    reject: (id) => api.put(`/brokers/${id}/reject`),
    dashboard: (brokerId) => api.get(`/brokers/${brokerId}/dashboard`),
    workers: (brokerId) => api.get(`/brokers/${brokerId}/workers`),
    placements: (brokerId) => api.get(`/brokers/${brokerId}/placements`),
    allPlacements: () => api.get('/brokers/placements'),
    eligibleJobs: (brokerId, workerId) =>
        api.get(`/brokers/${brokerId}/workers/${workerId}/eligible-jobs`),
    addWorker: (brokerId, payload) => api.post(`/brokers/${brokerId}/workers`, payload),
    setWorkerStatus: (brokerId, workerId, status) =>
        api.put(`/brokers/${brokerId}/workers/${workerId}/status?status=${encodeURIComponent(status)}`),
    recordPlacement: (brokerId, workerId, payload) =>
        api.post(`/brokers/${brokerId}/workers/${workerId}/placements`, payload),
};
