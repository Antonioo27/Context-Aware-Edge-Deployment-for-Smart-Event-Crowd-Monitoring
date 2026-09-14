/**
 * Real-time system performance metrics banner component.
 * Displays latency benchmarks and workload telemetry across four core key performance indicators:
 * Fast-Path reaction time (Sensor to Edge WS), Slow-Path persistence latency (Cloud DB sync),
 * total processed probe batches, and cumulative linear regression alerts.
 */

import React, { useEffect, useState } from 'react';
import { metricsApi } from '../../../api/metricsApi';
import type { SystemMetricsDTO } from '../../../types';

/**
 * Renders the top metrics bar with periodic 3-second polling to display real-time latency and throughput KPIs.
 *
 * @returns Rendered JSX metrics summary row, or null if telemetry data is not yet loaded.
 */
export const MetricsSummary: React.FC = () => {
  const [metrics, setMetrics] = useState<SystemMetricsDTO | null>(null);

  /**
   * Fetches latest system telemetry metrics from the backend API and updates local state.
   */
  const fetchMetrics = async () => {
    try {
      const data = await metricsApi.getSystemMetrics();
      setMetrics(data);
    } catch (err) {
      console.error('Error fetching system metrics:', err);
    }
  };

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 3000);
    return () => clearInterval(interval);
  }, []);

  if (!metrics) return null;

  return (
    <div className="row g-2 mb-3">
      <div className="col-md-3">
        <div className="p-2 border rounded bg-white shadow-sm h-100">
          <div className="d-flex justify-content-between align-items-center">
            <span className="text-secondary small fw-bold text-uppercase">Reattività Alert (Fast-Path)</span>
          </div>
          <div className="d-flex align-items-baseline gap-2 mt-1">
            <h3 className={`mb-0 fw-bold ${metrics.instantFastPathLatencyMs < 20 ? 'text-success' : 'text-danger'}`}>
              {metrics.instantFastPathLatencyMs?.toFixed(1) ?? '0.0'}
            </h3>
            <span className="text-muted small">ms</span>
          </div>
          <div className="text-muted" style={{ fontSize: '0.75rem' }}>
            Latenza diretta Sensore ➔ Edge WS
          </div>
        </div>
      </div>

      <div className="col-md-3">
        <div className="p-2 border rounded bg-white shadow-sm h-100">
          <div className="d-flex justify-content-between align-items-center">
            <span className="text-secondary small fw-bold text-uppercase">Persistenza DB (Slow-Path)</span>
          </div>
          <div className="d-flex align-items-baseline gap-2 mt-1">
            <h3 className="mb-0 fw-bold text-primary">
              {metrics.instantAverageLatencyMs?.toFixed(1) ?? '0.0'}
            </h3>
            <span className="text-muted small">ms</span>
          </div>
          <div className="text-muted" style={{ fontSize: '0.75rem' }}>
            Latenza Ingress + Egress Cloud
          </div>
        </div>
      </div>

      <div className="col-md-3">
        <div className="p-2 border rounded bg-white shadow-sm h-100">
          <div className="d-flex justify-content-between align-items-center">
            <span className="text-secondary small fw-bold text-uppercase">Richieste Elaborate</span>
          </div>
          <div className="d-flex align-items-baseline gap-2 mt-1">
            <h3 className="mb-0 fw-bold text-indigo">
              {metrics.totalRequestsProcessed?.toLocaleString() ?? '0'}
            </h3>
            <span className="text-muted small">batch</span>
          </div>
          <div className="text-muted" style={{ fontSize: '0.75rem' }}>
            Flusso ingress continuo
          </div>
        </div>
      </div>

      <div className="col-md-3">
        <div className="p-2 border rounded bg-white shadow-sm h-100">
          <div className="d-flex justify-content-between align-items-center">
            <span className="text-secondary small fw-bold text-uppercase">Alert Registrati</span>
          </div>
          <div className="d-flex align-items-baseline gap-2 mt-1">
            <h3 className="mb-0 fw-bold text-danger">
              {metrics.totalAlertsCount ?? 0}
            </h3>
            <span className="text-muted small">eventi</span>
          </div>
          <div className="text-muted" style={{ fontSize: '0.75rem' }}>
            Rilevati da regressione lineare
          </div>
        </div>
      </div>
    </div>
  );
};

export default MetricsSummary;