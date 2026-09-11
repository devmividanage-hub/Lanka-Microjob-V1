import { api } from './client.js';

/** matching-service (/matches) - rule-based skill scoring shown as a match percentage. */
export const matchesApi = {
    score: (payload) => api.post('/matches', payload),
    scoreBatch: (payload) => api.post('/matches/batch', payload),
    history: (jobId) => api.get(`/matches/${jobId}`),
};
