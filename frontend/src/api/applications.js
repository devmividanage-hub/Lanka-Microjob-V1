import { api } from './client.js';

/** job-service (/applications) - the real, database-backed application workflow. */
export const applicationsApi = {
    apply: (jobId, message) => api.post('/applications', { jobId, message }),
    mine: () => api.get('/applications/mine'),
    byWorker: (workerId) => api.get(`/applications/worker/${workerId}`),
    byEmployer: (employerId) => api.get(`/applications/employer/${employerId}`),
    byJob: (jobId) => api.get(`/applications/job/${jobId}`),
    accept: (id) => api.put(`/applications/${id}/accept`),
    reject: (id) => api.put(`/applications/${id}/reject`),
    cancel: (id) => api.put(`/applications/${id}/cancel`),
    complete: (id) => api.put(`/applications/${id}/complete`),
    workerSummary: () => api.get('/applications/summary/worker'),
    employerSummary: () => api.get('/applications/summary/employer'),
};
