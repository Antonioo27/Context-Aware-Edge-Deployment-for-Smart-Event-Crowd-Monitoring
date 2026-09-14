/**
 * Event creation view component.
 * Provides a structured form for initializing a new smart crowd monitoring event
 * with metadata (name, description, location, and city) before redirecting to the main dashboard.
 */

import React, { useState } from 'react';
import type { EventDTO } from '../../../types';
import { eventApi } from '../../../api/eventApi';
import { Card, CardHeader, CardBody } from '../../ui/Card';

interface Props {
  onEventCreated: () => void;
  onSkipToDashboard: () => void;
}

/**
 * Renders the event creation form card and orchestrates submission to the Event Management API.
 *
 * @param props Component properties containing navigation callbacks for creation success and dashboard skip.
 * @returns Rendered JSX form component.
 */
const CreateEvent: React.FC<Props> = ({ onEventCreated, onSkipToDashboard }) => {
  const [formData, setFormData] = useState<EventDTO>({
    name: '',
    description: '',
    location: '',
    city: ''
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  /**
   * Updates form state dynamically when input fields or textareas change.
   *
   * @param e Standard React change event from input or textarea elements.
   */
  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  /**
   * Submits the event data transfer object to the backend API.
   *
   * @param e Standard React form submission event.
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await eventApi.createEvent(formData);
      onEventCreated();
    } catch (err: any) {
      setError(err.message || 'Error creating event');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container py-5">
      <div className="row justify-content-center">
        <div className="col-md-8">
          <Card>
            <CardHeader className="bg-primary text-white d-flex justify-content-between align-items-center">
              <h5 className="mb-0">Crea Nuovo Evento</h5>
              <button 
                type="button" 
                className="btn btn-outline-light btn-sm"
                onClick={onSkipToDashboard}
              >
                Vai alla Dashboard ➔
              </button>
            </CardHeader>
            <CardBody className="p-4">
              {error && <div className="alert alert-danger">{error}</div>}
              <form onSubmit={handleSubmit}>
                <div className="mb-3">
                  <label htmlFor="name" className="form-label">Nome Evento</label>
                  <input type="text" className="form-control" id="name" name="name" required value={formData.name} onChange={handleChange} data-testid="create-event-name" />
                </div>
                <div className="mb-3">
                  <label htmlFor="description" className="form-label">Descrizione</label>
                  <textarea className="form-control" id="description" name="description" rows={3} required value={formData.description} onChange={handleChange} data-testid="create-event-description"></textarea>
                </div>
                <div className="mb-3">
                  <label htmlFor="location" className="form-label">Luogo (es. Piazza Maggiore)</label>
                  <input type="text" className="form-control" id="location" name="location" required value={formData.location} onChange={handleChange} data-testid="create-event-location" />
                </div>
                <div className="mb-3">
                  <label htmlFor="city" className="form-label">Città</label>
                  <input type="text" className="form-control" id="city" name="city" required value={formData.city} onChange={handleChange} data-testid="create-event-city" />
                </div>
                <div className="d-flex justify-content-between align-items-center mt-4">
                  <button type="submit" className="btn btn-primary" disabled={loading} data-testid="create-event-submit">
                    {loading ? 'Creazione in corso...' : 'Crea Evento'}
                  </button>
                </div>
              </form>
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default CreateEvent;