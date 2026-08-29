import fetchClient from './client';
import type { SystemMetricsDTO } from '../types';

export const metricsApi = {
  getSystemMetrics: () => 
    fetchClient<SystemMetricsDTO>('/api/metrics', { method: 'GET' })
};