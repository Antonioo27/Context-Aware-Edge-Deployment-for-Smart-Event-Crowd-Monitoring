import { useState, useCallback } from 'react';
import { areaApi } from '../api/areaApi';
import { nodeApi } from '../api/nodeApi';
import type { AreaDTO, NodeDTO } from '../types';
import { usePolling } from './usePolling';

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
      console.error("Impossibile caricare i dati della dashboard", e);
      setError(e instanceof Error ? e : new Error('Failed to fetch dashboard data'));
    } finally {
      setLoading(false);
    }
  }, []);

  // Use the custom polling hook
  usePolling(fetchData, pollingInterval);

  return {
    areas,
    nodes,
    loading,
    error,
    refreshData: fetchData
  };
}
