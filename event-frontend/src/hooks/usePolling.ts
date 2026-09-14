/**
 * Generic polling hook for periodic asynchronous tasks.
 * Executes a callback at specified millisecond intervals while maintaining the latest callback reference
 * across re-renders without causing stale closures or resetting active timers.
 */

import { useEffect, useRef } from 'react';

type Callback = () => void | Promise<void>;

/**
 * Periodically invokes the provided callback function at the given interval.
 * Executes the callback immediately on mount before establishing the recurring interval.
 *
 * @param callback The function or asynchronous task to invoke periodically.
 * @param delay The interval delay in milliseconds, or null to disable scheduling.
 */
export function usePolling(callback: Callback, delay: number | null): void {
  const savedCallback = useRef<Callback | null>(null);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (delay === null) {
      return;
    }

    const tick = async () => {
      if (savedCallback.current) {
        await savedCallback.current();
      }
    };

    tick();

    const id = setInterval(tick, delay);
    return () => clearInterval(id);
  }, [delay]);
}
