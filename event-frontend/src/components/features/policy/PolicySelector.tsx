/**
 * Orchestrator policy selector component.
 * Allows operators to dynamically switch between placement algorithms
 * (CONTEXT_AWARE, CLOUD_ONLY, STATIC) in the edge orchestrator service.
 */

import React, { useEffect, useState } from 'react';
import { orchestrationApi, type OrchestrationPolicy } from '../../../api/orchestrationApi';

/**
 * Renders the policy selection dropdown, fetching initial configuration and pushing changes to the orchestrator.
 *
 * @returns Rendered JSX policy selector element.
 */
const PolicySelector: React.FC = () => {
  const [policy, setPolicy] = useState<OrchestrationPolicy>('CONTEXT_AWARE');
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    orchestrationApi.getPolicy()
      .then(currentPolicy => {
        if (currentPolicy) setPolicy(currentPolicy);
      })
      .catch(err => console.error('Error fetching initial orchestrator policy:', err));
  }, []);

  /**
   * Updates the active orchestration policy via the backend API.
   *
   * @param newPolicy The selected placement policy strategy.
   */
  const handleChangePolicy = async (newPolicy: OrchestrationPolicy) => {
    setLoading(true);
    try {
      await orchestrationApi.setPolicy(newPolicy);
      setPolicy(newPolicy);
    } catch (err) {
      console.error('Error updating orchestrator policy:', err);
      alert('Unable to update orchestrator policy.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="d-flex align-items-center gap-2 bg-white px-2 py-1 rounded border shadow-sm">
      <label htmlFor="orchestration-policy-select" className="small fw-bold text-muted text-nowrap mb-0">
        Strategia Orchestratore:
      </label>
      <select
        id="orchestration-policy-select"
        className="form-select form-select-sm border-primary fw-semibold"
        style={{ width: 'auto', minWidth: '160px', cursor: 'pointer' }}
        value={policy}
        disabled={loading}
        onChange={(e) => handleChangePolicy(e.target.value as OrchestrationPolicy)}
      >
        <option value="CONTEXT_AWARE">CONTEXT_AWARE</option>
        <option value="CLOUD_ONLY">CLOUD_ONLY</option>
        <option value="STATIC">STATIC</option>
      </select>
      {loading && <div className="spinner-border spinner-border-sm text-primary" role="status" />}
    </div>
  );
};

export default PolicySelector;