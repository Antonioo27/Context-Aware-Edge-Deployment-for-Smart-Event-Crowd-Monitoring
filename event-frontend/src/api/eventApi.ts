import fetchClient from './client';
import type { EventDTO, Event } from '../types';

export const eventApi = {
  getEvent: () => fetchClient<Event>('/api/event'),
  createEvent: (event: EventDTO) => 
    fetchClient<string>('/api/event', {
      method: 'POST',
      body: JSON.stringify(event),
    }),
  deleteEvent: () => fetchClient<string>('/api/event', { method: 'DELETE' }),
};
