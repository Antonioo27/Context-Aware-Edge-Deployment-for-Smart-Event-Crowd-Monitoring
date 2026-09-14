/**
 * API service module for Kubernetes orchestration policies.
 * Enables inspecting and dynamically switching the active pod placement strategy.
 */

import fetchClient from './client';

export type OrchestrationPolicy = 'CONTEXT_AWARE' | 'CLOUD_ONLY' | 'STATIC';

export const orchestrationApi = {
  /**
   * Retrieves the currently active orchestration policy governing pod placement.
   *
   * @returns A Promise resolving to the current OrchestrationPolicy string.
   */
  getPolicy: (): Promise<OrchestrationPolicy> =>
    fetchClient<OrchestrationPolicy>('/api/orchestration/policy', { method: 'GET' }),

  /**
   * Updates the active orchestration policy applied by the orchestrator control loop.
   *
   * @param policy The desired OrchestrationPolicy (CONTEXT_AWARE, CLOUD_ONLY, STATIC).
   * @returns A Promise resolving to the confirmation response string.
   */
  setPolicy: (policy: OrchestrationPolicy): Promise<string> =>
    fetchClient<string>(`/api/orchestration/policy?policy=${encodeURIComponent(policy)}`, {
      method: 'POST',
    }),
};