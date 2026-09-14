/**
 * API service module for alert and notification management.
 * Handles role-based notification queries and manual incident reporting with geographic coordinates.
 */

import fetchClient from './client';
import type { NotifyDTO, UserType, ManualAlertDTO } from '../types';

export const alertApi = {
  /**
   * Retrieves contextual notifications tailored for a specific user role (USER, OPERATOR, ORGANIZER).
   *
   * @param userType The target user audience profile.
   * @returns A Promise resolving to an array of NotifyDTO objects.
   */
  getAlerts: (userType: UserType): Promise<NotifyDTO[]> =>
    fetchClient<NotifyDTO[]>(`/api/event/notify-${userType.toLowerCase()}`, { method: 'POST' }),

  /**
   * Submits a manual alert with geographic coordinates and cause description.
   *
   * @param alert The ManualAlertDTO payload containing timestamp, cause, latitude, and longitude.
   * @returns A Promise resolving to the confirmation response string.
   */
  sendManualAlert: (alert: ManualAlertDTO): Promise<string> =>
    fetchClient<string>('/api/event/area/manual-alert', {
      method: 'POST',
      body: JSON.stringify(alert),
    }),
};
