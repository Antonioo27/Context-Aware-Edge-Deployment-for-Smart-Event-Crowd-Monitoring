import fetchClient from './client';
import type { NodeDTO } from '../types';

export const nodeApi = {
  getNodes: () =>
    fetchClient<NodeDTO[]>('/api/nodes', { method: 'GET' }),
  createNode: (node: NodeDTO) => 
    fetchClient<NodeDTO>('/api/nodes', {
      method: 'POST',
      body: JSON.stringify(node),
    }),
  updateNode: (id: string, node: NodeDTO) =>
    fetchClient<NodeDTO>(`/api/nodes/${id}`, {
      method: 'PUT',
      body: JSON.stringify(node),
    }),
  syncK8sNodes: () => fetchClient<NodeDTO[]>('/api/nodes/sync-k8s', { method: 'POST' }),
  getNodeAllocations: () =>
    fetchClient<Record<string, string[]>>('/api/nodes/allocations', { method: 'GET' }),
};
