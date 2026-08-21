import fetchClient from './client';
import type { NotifyDTO, UserType, ManualAlertDTO } from '../types';

export const alertApi = {
  getAlerts: (userType: UserType) =>
    fetchClient<NotifyDTO[]>(`/api/event/notify-${userType.toLowerCase()}`, { method: 'POST' }),
  sendManualAlert: (alert: ManualAlertDTO) =>
    fetchClient<string>('/api/event/manual-alert', { 
      method: 'POST', 
      body: JSON.stringify(alert) 
    })
};
