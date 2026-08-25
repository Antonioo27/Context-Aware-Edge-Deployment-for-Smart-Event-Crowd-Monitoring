import fetchClient from './client';

export type OrchestrationPolicy = 'CONTEXT_AWARE' | 'CLOUD_ONLY' | 'STATIC';

export const orchestrationApi = {
  getPolicy: () => 
    fetchClient<OrchestrationPolicy>('/api/orchestration/policy', { method: 'GET' }),

  setPolicy: (policy: OrchestrationPolicy) => 
    fetchClient<string>(`/api/orchestration/policy?policy=${encodeURIComponent(policy)}`, {
      method: 'POST',
    }),
};