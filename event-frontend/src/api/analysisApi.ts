/**
 * API service module for crowd analytics and forecasting.
 * Provides access to historical metrics and future prediction trend lines for individual areas.
 */

import fetchClient from './client';
import type { AnalysisStats, PredictionPointDTO } from '../types';

export const analysisApi = {
  /**
   * Retrieves the full chronological history of analysis measurements recorded for an area.
   *
   * @param areaId The unique identifier of the area.
   * @returns A Promise resolving to an array of AnalysisStats items.
   */
  getAnalysisAll: (areaId: string): Promise<AnalysisStats[]> =>
    fetchClient<AnalysisStats[]>(`/api/event/area/${areaId}/analysis-all`, { method: 'GET' }),

  /**
   * Retrieves future prediction points for an area computed via weighted damped regression.
   *
   * @param areaId The unique identifier of the area.
   * @returns A Promise resolving to an array of PredictionPointDTO items.
   */
  getPredictionTrend: (areaId: string): Promise<PredictionPointDTO[]> =>
    fetchClient<PredictionPointDTO[]>(`/api/event/area/${areaId}/prediction`, { method: 'GET' }),
};
