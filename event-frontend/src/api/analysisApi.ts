import fetchClient from './client';
import type { AnalysisStats, PredictionPointDTO } from '../types';

export const analysisApi = {
  getAnalysisAll: (areaId: string) =>
    fetchClient<AnalysisStats[]>(`/api/event/area/${areaId}/analysis-all`, { method: 'GET' }),
  getPredictionTrend: (areaId: string) =>
    fetchClient<PredictionPointDTO[]>(`/api/event/area/${areaId}/prediction`, { method: 'GET' })
};
