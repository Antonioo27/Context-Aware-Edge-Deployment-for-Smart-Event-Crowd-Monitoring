import React, { useEffect, useState } from 'react';
import { orchestrationApi, type OrchestrationPolicy } from '../../../api/orchestrationApi';

const PolicySelector: React.FC = () => {
  const [policy, setPolicy] = useState<OrchestrationPolicy>('CONTEXT_AWARE');
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    orchestrationApi.getPolicy()
      .then(currentPolicy => {
        if (currentPolicy) setPolicy(currentPolicy);
      })
      .catch(err => console.error('Errore nel recupero della policy iniziale:', err));
  }, []);

  const handleChangePolicy = async (newPolicy: OrchestrationPolicy) => {
    setLoading(true);
    try {
      await orchestrationApi.setPolicy(newPolicy);
      setPolicy(newPolicy);
    } catch (err) {
      console.error('Errore nel cambio policy:', err);
      alert('Impossibile aggiornare la politica dell\'orchestratore.');
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