import fetchClient from './client';
import type { AreaDTO } from '../types';

export const areaApi = {
  getAreas: () =>
    fetchClient<AreaDTO[]>('/api/event/areas', { method: 'GET' }),
  createArea: (area: AreaDTO) => 
    fetchClient<string>('/api/event/area', {
      method: 'POST',
      body: JSON.stringify(area),
    }),
  deleteArea: (areaId: string) => 
    fetchClient<string>(`/api/event/area/${areaId}`, { method: 'DELETE' }),
  updateArea: (areaId: string, area: AreaDTO) =>
    fetchClient<AreaDTO>(`/api/event/area/${areaId}`, {
      method: 'PUT',
      body: JSON.stringify(area),
    })
};
