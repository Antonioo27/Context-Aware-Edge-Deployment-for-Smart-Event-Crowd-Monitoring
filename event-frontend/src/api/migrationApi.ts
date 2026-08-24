import fetchClient from './client';
import type { MigrationDTO } from '../types';


export const migrationApi = {
  getAllMigrations: () => 
    fetchClient<MigrationDTO[]>('/api/orchestration/migrations', { method: 'GET' }),
  
  getMigrationsByArea: (areaId: string) => 
    fetchClient<MigrationDTO[]>(`/api/orchestration/migrations/area/${areaId}`, { method: 'GET' }),

  deleteAllMigrations: () => 
    fetchClient<string>('/api/orchestration/migrations', { method: 'DELETE' }),
};