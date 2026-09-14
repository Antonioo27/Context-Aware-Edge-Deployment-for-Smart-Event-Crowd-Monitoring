/**
 * API service module for global Event lifecycle operations.
 * Handles fetching, creation, and cascading deletion of the central event configuration.
 */

import fetchClient from './client';
import type { EventDTO, Event } from '../types';

export const eventApi = {
  /**
   * Retrieves the currently active event from the backend.
   *
   * @returns A Promise resolving to the active Event entity.
   */
  getEvent: (): Promise<Event> => fetchClient<Event>('/api/event'),

  /**
   * Creates a new event with the given metadata.
   *
   * @param event The EventDTO payload containing name, description, location, and city.
   * @returns A Promise resolving to a confirmation message string.
   */
  createEvent: (event: EventDTO): Promise<string> =>
    fetchClient<string>('/api/event', {
      method: 'POST',
      body: JSON.stringify(event),
    }),

  /**
   * Deletes the active event and triggers cascading cleanup of areas, analytics, alerts, and pods.
   *
   * @returns A Promise resolving to a confirmation message string.
   */
  deleteEvent: (): Promise<string> => fetchClient<string>('/api/event', { method: 'DELETE' }),
};
