/** Job creation: POST /jobs with the employer identity taken from the JWT on the server. */
import { jobsApi } from '../api/jobs.js';
import { currentUser, isApproved } from '../auth/session.js';
import { navigate } from '../services/router.js';
import { $, clearSkills, esc, selectedSkills, setText, show, toast, value, withBusyButton } from '../utils/dom.js';
import { money, todayIso } from '../utils/format.js';
import { cascadeCity } from '../utils/locations.js';

export function updateJobPreview() {
    const title = value('#pj-title') || '—';
    const count = Number($('#pj-count')?.value || 0);
    const pay = Number($('#pj-pay')?.value || 0);
    setText('#prev-title', title);
    setText('#prev-cat', value('#pj-cat') || '—');
    setText('#prev-dist', value('#pj-dist') || '—');
    setText('#prev-city', value('#pj-city') || '—');
    setText('#prev-count', `${count} worker${count === 1 ? '' : 's'}`);
    setText('#prev-pay', count && pay ? `${money(count * pay)} total` : '—');
    setText('#prev-skills', selectedSkills('#page-post-job .skills-grid').join(', ') || 'No specific skills');
}

export function onPostJobDistrictChange() {
    cascadeCity('pj-dist', 'pj-city-field', 'pj-city', updateJobPreview);
    updateJobPreview();
}

export function initPostJobForm() {
    const dateInput = $('#pj-date');
    if (dateInput && !dateInput.value) {
        dateInput.min = todayIso();
        dateInput.value = todayIso();
    }
    const user = currentUser();
    if (user?.district) {
        const districtSelect = $('#pj-dist');
        if (districtSelect && !districtSelect.value) {
            districtSelect.value = user.district;
            cascadeCity('pj-dist', 'pj-city-field', 'pj-city');
            const citySelect = $('#pj-city');
            if (citySelect && user.city) citySelect.value = user.city;
        }
    }
    show('#post-job-guest', !user?.token || !isApproved(user));
    show('#post-job-form', Boolean(user?.token) && isApproved(user));
    updateJobPreview();
}

export async function submitJob(button) {
    const user = currentUser();
    if (!user?.token || user.role !== 'EMPLOYER') {
        toast('Sign in as an approved employer to post a job', 'coral');
        return navigate('employer-auth');
    }
    const payload = {
        title: value('#pj-title'),
        category: value('#pj-cat'),
        district: value('#pj-dist'),
        city: value('#pj-city'),
        workersNeeded: Number($('#pj-count')?.value || 0),
        payPerWorker: Number($('#pj-pay')?.value || 0),
        jobDate: value('#pj-date'),
        urgent: $('#pj-urgent')?.checked === true,
        requiredSkills: selectedSkills('#page-post-job .skills-grid').join(','),
        additionalNotes: value('#pj-notes'),
    };
    const errorEl = $('#post-job-error');
    const fail = (message) => {
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';
        }
        toast(message, 'coral');
    };
    if (!payload.title) return fail('Job title is required');
    if (!payload.district) return fail('District is required');
    if (!payload.workersNeeded || payload.workersNeeded < 1) return fail('At least one worker is needed');
    if (!payload.payPerWorker || payload.payPerWorker < 1) return fail('Pay per worker must be a positive amount');
    if (!payload.jobDate) return fail('Job date is required');
    if (errorEl) errorEl.style.display = 'none';

    await withBusyButton(button, async () => {
        try {
            const job = await jobsApi.create(payload);
            toast(`Job posted - it is now live in the worker feed (#${job.id})`, 'amber');
            resetPostJobForm();
            navigate('employer-dash');
        } catch (error) {
            fail(error.firstFieldError ? `${error.message}: ${error.firstFieldError}` : (error.message || 'Could not post the job'));
        }
    }, 'Posting…');
}

export function resetPostJobForm() {
    ['#pj-title', '#pj-pay', '#pj-notes'].forEach(selector => {
        const el = $(selector);
        if (el) el.value = '';
    });
    const count = $('#pj-count');
    if (count) count.value = '2';
    const urgent = $('#pj-urgent');
    if (urgent) urgent.checked = false;
    clearSkills('#page-post-job .skills-grid');
    show('#pj-preview', false);
    updateJobPreview();
}
