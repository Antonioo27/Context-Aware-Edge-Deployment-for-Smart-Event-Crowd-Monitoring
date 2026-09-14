/**
 * API service module for Area management.
 * Encapsulates CRUD endpoints for geographical monitoring zones, polygon coordinates, and capacities.
 */

import fetchClient from './client';
import type { AreaDTO } from '../types';

export const areaApi = {
  /**
   * Retrieves all defined monitoring areas including their boundaries, states, and capacities.
   *
   * @returns A Promise resolving to an array of AreaDTO objects.
   */
  getAreas: (): Promise<AreaDTO[]> =>
    fetchClient<AreaDTO[]>('/api/event/areas', { method: 'GET' }),

  /**
   * Creates a new monitoring area and triggers the automatic deployment of its analysis pod on Kubernetes.
   *
   * @param area The AreaDTO payload containing name, capacity, priority, type, and polygon coordinates.
   * @returns A Promise resolving to the creation response message.
   */
  createArea: (area: AreaDTO): Promise<string> =>
    fetchClient<string>('/api/event/area', {
      method: 'POST',
      body: JSON.stringify(area),
    }),

  /**
   * Deletes an area by its unique identifier and removes its corresponding Kubernetes deployment.
   *
   * @param areaId The unique name or identifier of the area to delete.
   * @returns A Promise resolving to the deletion response message.
   */
  deleteArea: (areaId: string): Promise<string> =>
    fetchClient<string>(`/api/event/area/${areaId}`, { method: 'DELETE' }),

  /**
   * Updates an existing area's metadata, capacity, or boundary polygon.
   *
   * @param areaId The identifier of the area being updated.
   * @param area The updated AreaDTO configuration.
   * @returns A Promise resolving to the updated AreaDTO object.
   */
  updateArea: (areaId: string, area: AreaDTO): Promise<AreaDTO> =>
    fetchClient<AreaDTO>(`/api/event/area/${areaId}`, {
      method: 'PUT',
      body: JSON.stringify(area),
    }),
};
