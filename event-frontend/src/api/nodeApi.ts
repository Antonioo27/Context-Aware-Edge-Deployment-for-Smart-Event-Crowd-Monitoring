/**
 * API service module for Node management and infrastructure simulation.
 * Covers node discovery, GPS geolocation, pod allocations, CPU metrics,
 * and experimental chaos testing controls (CPU stress, node cordoning).
 */

import fetchClient from './client';
import type { NodeDTO } from '../types';

export const nodeApi = {
  /**
   * Retrieves all registered edge and cloud nodes from the database.
   *
   * @returns A Promise resolving to an array of NodeDTO objects.
   */
  getNodes: (): Promise<NodeDTO[]> =>
    fetchClient<NodeDTO[]>('/api/nodes', { method: 'GET' }),

  /**
   * Registers a new computational node in the system.
   *
   * @param node The NodeDTO payload including node ID, broker URL, and geographic coordinates.
   * @returns A Promise resolving to the created NodeDTO.
   */
  createNode: (node: NodeDTO): Promise<NodeDTO> =>
    fetchClient<NodeDTO>('/api/nodes', {
      method: 'POST',
      body: JSON.stringify(node),
    }),

  /**
   * Updates an existing node's geographic position and broker configuration.
   *
   * @param id The unique identifier of the node.
   * @param node The updated NodeDTO object with new latitude, longitude, or broker URL.
   * @returns A Promise resolving to the updated NodeDTO.
   */
  updateNode: (id: string, node: NodeDTO): Promise<NodeDTO> =>
    fetchClient<NodeDTO>(`/api/nodes/${id}`, {
      method: 'PUT',
      body: JSON.stringify(node),
    }),

  /**
   * Triggers Kubernetes node auto-discovery on the backend, synchronizing cluster nodes into the database.
   *
   * @returns A Promise resolving to the list of discovered and synchronized NodeDTOs.
   */
  syncK8sNodes: (): Promise<NodeDTO[]> =>
    fetchClient<NodeDTO[]>('/api/nodes/sync-k8s', { method: 'POST' }),

  /**
   * Fetches the current assignment mapping of analysis pods across all cluster nodes.
   *
   * @returns A Promise resolving to a map where keys are node IDs and values are arrays of hosted pod names.
   */
  getNodeAllocations: (): Promise<Record<string, string[]>> =>
    fetchClient<Record<string, string[]>>('/api/nodes/allocations', { method: 'GET' }),

  /**
   * Retrieves real-time CPU usage percentages for each node from the Kubernetes Metrics Server.
   *
   * @returns A Promise resolving to a map of node IDs to their CPU utilization percentages.
   */
  getNodeCpuMetrics: (): Promise<Record<string, number>> =>
    fetchClient<Record<string, number>>('/api/nodes/nodeUsage', { method: 'GET' }),

  /**
   * Launches a stress-ng container on the specified node to simulate synthetic CPU overload.
   *
   * @param nodeId The target node identifier.
   * @param duration Duration of the stress test in seconds (defaults to 180).
   * @returns A Promise resolving to the stress test status and execution message.
   */
  startCpuStress: (
    nodeId: string,
    duration: number = 180
  ): Promise<{ nodeId: string; stressActive: boolean; message: string }> =>
    fetchClient<{ nodeId: string; stressActive: boolean; message: string }>(
      `/api/nodes/${nodeId}/stress?duration=${duration}`,
      { method: 'POST' }
    ),

  /**
   * Terminates any active stress-ng pod currently running on the specified node.
   *
   * @param nodeId The target node identifier.
   * @returns A Promise resolving to the termination status and message.
   */
  stopCpuStress: (
    nodeId: string
  ): Promise<{ nodeId: string; stressActive: boolean; message: string }> =>
    fetchClient<{ nodeId: string; stressActive: boolean; message: string }>(
      `/api/nodes/${nodeId}/stress`,
      { method: 'DELETE' }
    ),

  /**
   * Marks a node as cordoned (unschedulable) or uncordoned in Kubernetes to simulate offline maintenance or failure.
   *
   * @param nodeId The target node identifier.
   * @param cordon True to cordon (mark unschedulable), false to uncordon.
   * @returns A Promise resolving to the operation result.
   */
  toggleCordonNode: (
    nodeId: string,
    cordon: boolean
  ): Promise<{ nodeId: string; cordoned: boolean; success: boolean; message: string }> =>
    fetchClient<{ nodeId: string; cordoned: boolean; success: boolean; message: string }>(
      `/api/nodes/${nodeId}/cordon?cordon=${cordon}`,
      { method: 'POST' }
    ),

  /**
   * Retrieves the current simulation status, listing all currently stressed and cordoned nodes.
   *
   * @returns A Promise resolving to arrays of stressed and cordoned node IDs.
   */
  getSimulationStatus: (): Promise<{ stressedNodes: string[]; cordonedNodes: string[] }> =>
    fetchClient<{ stressedNodes: string[]; cordonedNodes: string[] }>(
      '/api/nodes/simulation-status',
      { method: 'GET' }
    ),
};
