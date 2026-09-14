/**
 * Custom React hook for fetching and polling core dashboard data.
 * Aggregates monitoring areas and infrastructure nodes from the backend services,
 * managing loading, error handling, and periodic background synchronization.
 */

import { useState, useCallback } from 'react';
import { areaApi } from '../api/areaApi';
import { nodeApi } from '../api/nodeApi';
import type { AreaDTO, NodeDTO } from '../types';
import { usePolling } from './usePolling';

/**
 * Manages dashboard state by fetching areas and nodes in parallel, configuring
 * recurring polling, and providing manual refresh capabilities.
 *
 * @param pollingInterval The time delay between polling cycles in milliseconds (defaults to 20000 ms).
 * @returns An object containing the list of areas, nodes, loading state, error details, and a manual refresh trigger function.
 */
export function useDashboardData(pollingInterval: number = 20000) {
  const [areas, setAreas] = useState<AreaDTO[]>([]);
  const [nodes, setNodes] = useState<NodeDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchData = useCallback(async () => {
    try {
      const [areasData, nodesData] = await Promise.all([
        areaApi.getAreas(),
        nodeApi.getNodes()
      ]);
      setAreas(areasData);
      setNodes(nodesData);
      setError(null);
    } catch (e) {
      console.error('Failed to load dashboard data', e);
      setError(e instanceof Error ? e : new Error('Failed to fetch dashboard data'));
    } finally {
      setLoading(false);
    }
  }, []);

  usePolling(fetchData, pollingInterval);

  return {
    areas,
    nodes,
    loading,
    error,
    refreshData: fetchData
  };
}
