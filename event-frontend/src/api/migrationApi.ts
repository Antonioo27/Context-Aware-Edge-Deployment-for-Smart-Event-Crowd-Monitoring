/**
 * API service module for migration audit logs.
 * Provides functions to retrieve historical pod migration records and reset audit logs.
 */

import fetchClient from './client';
import type { MigrationDTO } from '../types';

export const migrationApi = {
  /**
   * Retrieves all recorded pod migrations ordered chronologically descending.
   *
   * @returns A Promise resolving to an array of MigrationDTO objects.
   */
  getAllMigrations: (): Promise<MigrationDTO[]> =>
    fetchClient<MigrationDTO[]>('/api/orchestration/migrations', { method: 'GET' }),

  /**
   * Retrieves recorded pod migrations filtered for a specific area.
   *
   * @param areaId The unique identifier of the area.
   * @returns A Promise resolving to an array of MigrationDTO objects for that area.
   */
  getMigrationsByArea: (areaId: string): Promise<MigrationDTO[]> =>
    fetchClient<MigrationDTO[]>(`/api/orchestration/migrations/area/${areaId}`, { method: 'GET' }),

  /**
   * Deletes all recorded migration audit events from the database.
   *
   * @returns A Promise resolving to a confirmation message string.
   */
  deleteAllMigrations: (): Promise<string> =>
    fetchClient<string>('/api/orchestration/migrations', { method: 'DELETE' }),
};