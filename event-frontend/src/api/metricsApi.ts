/**
 * API service module for system-wide performance and network metrics.
 * Fetches dual-path latencies, throughput statistics, CPU usage, and operational alert counts.
 */

import fetchClient from './client';
import type { SystemMetricsDTO } from '../types';

export const metricsApi = {
  /**
   * Retrieves aggregated system metrics including fast/slow path latencies and resource usage.
   *
   * @returns A Promise resolving to a SystemMetricsDTO object.
   */
  getSystemMetrics: (): Promise<SystemMetricsDTO> =>
    fetchClient<SystemMetricsDTO>('/api/metrics', { method: 'GET' }),
};