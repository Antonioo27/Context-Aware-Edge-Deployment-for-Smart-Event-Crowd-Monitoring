/**
 * Custom React hook for polling alerts filtered by user persona.
 * Fetches notification alerts targeted at specific audience roles (e.g., USER, POLICE, OPERATOR)
 * and maintains background polling updates.
 */

import { useState, useCallback } from 'react';
import { alertApi } from '../api/alertApi';
import type { NotifyDTO, UserType } from '../types';
import { usePolling } from './usePolling';

/**
 * Periodically retrieves alert notifications tailored for the specified user persona.
 *
 * @param userType The persona type to filter notifications for (e.g., USER, POLICE, OPERATOR).
 * @param pollingInterval The time delay between polling cycles in milliseconds (defaults to 10000 ms).
 * @returns An object containing the alert list, loading flag, error status, and a manual refresh trigger.
 */
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
      console.error('Failed to fetch alerts', e);
      setError(e instanceof Error ? e : new Error('Failed to fetch alerts'));
    } finally {
      setLoading(false);
    }
  }, [userType]);

  usePolling(fetchAlerts, pollingInterval);

  return {
    alerts,
    loading,
    error,
    refreshAlerts: fetchAlerts
  };
}
