import { authApi } from '../api/auth.js';
import { brokersApi } from '../api/brokers.js';
import { jobsApi } from '../api/jobs.js';
import { setText } from '../utils/dom.js';

const asCount = value => Number.isFinite(Number(value)) ? Number(value) : 0;

/** Load public, database-backed landing-page counters without requiring a signed-in user. */
export async function loadHomeStats() {
    setText('#home-worker-count', '…');
    setText('#home-job-count', '…');
    setText('#home-broker-count', '…');

    const [userResult, jobResult, brokerResult] = await Promise.allSettled([
        authApi.publicStats(),
        jobsApi.publicStats(),
        brokersApi.publicStats(),
    ]);

    if (userResult.status === 'fulfilled' && brokerResult.status === 'fulfilled') {
        const onlineWorkers = asCount(userResult.value.approvedWorkers);
        const offlineWorkers = asCount(brokerResult.value.totalOfflineWorkers);
        setText('#home-worker-count', String(onlineWorkers + offlineWorkers));
    } else {
        setText('#home-worker-count', '—');
    }

    setText('#home-job-count', jobResult.status === 'fulfilled'
        ? String(asCount(jobResult.value.totalJobs)) : '—');
    setText('#home-broker-count', brokerResult.status === 'fulfilled'
        ? String(asCount(brokerResult.value.approvedBrokers)) : '—');

    [userResult, jobResult, brokerResult].forEach(result => {
        if (result.status === 'rejected') console.warn('Unable to load a landing-page counter', result.reason);
    });
}
