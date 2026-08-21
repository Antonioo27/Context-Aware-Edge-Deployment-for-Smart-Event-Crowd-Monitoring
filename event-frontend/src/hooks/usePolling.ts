import { useEffect, useRef } from 'react';

type Callback = () => void | Promise<void>;

/**
 * Custom hook to poll a given callback function at a specified interval.
 * @param callback The function to call periodically.
 * @param delay The interval delay in milliseconds.
 */
export function usePolling(callback: Callback, delay: number | null) {
  const savedCallback = useRef<Callback | null>(null);

  // Remember the latest callback if it changes.
  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  // Set up the interval.
  useEffect(() => {
    // Don't schedule if no delay is specified.
    if (delay === null) {
      return;
    }

    const tick = async () => {
      if (savedCallback.current) {
        await savedCallback.current();
      }
    };

    // Execute immediately on mount
    tick();

    const id = setInterval(tick, delay);
    return () => clearInterval(id);
  }, [delay]);
}
