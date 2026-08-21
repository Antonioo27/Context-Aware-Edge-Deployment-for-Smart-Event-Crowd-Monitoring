import { useState, useCallback } from 'react';
import { alertApi } from '../api/alertApi';
import type { NotifyDTO, UserType } from '../types';
import { usePolling } from './usePolling';

export function useAlerts(userType: UserType, pollingInterval: number = 10000) {
  const [alerts, setAlerts] = useState<NotifyDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const fetchAlerts = useCallback(async () => {
    try {
      setLoading(true);
      const data = await alertApi.getAlerts(userType);
      setAlerts(data);
      setError(null);
    } catch (e) {
      console.error("Failed to fetch alerts", e);
      setError(e instanceof Error ? e : new Error('Failed to fetch alerts'));
    } finally {
      setLoading(false);
    }
  }, [userType]);

  // Use the custom polling hook
  usePolling(fetchAlerts, pollingInterval);

  return {
    alerts,
    loading,
    error,
    refreshAlerts: fetchAlerts
  };
}
