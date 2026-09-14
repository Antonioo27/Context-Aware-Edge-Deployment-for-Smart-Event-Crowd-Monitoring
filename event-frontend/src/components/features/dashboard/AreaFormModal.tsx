/**
 * Area creation modal dialog component.
 * Prompts operators to specify name, max capacity, area classification type, and operational priority
 * for newly digitized geographic polygon boundaries drawn on the map.
 */

import React, { useState } from 'react';
import { AreaType, Priority, type AreaDTO } from '../../../types';
import { Modal } from '../../ui/Modal';

interface Props {
  coordinates: number[][][];
  onSave: (area: AreaDTO) => void;
  onCancel: () => void;
}

/**
 * Renders a modal dialog form for assigning operational properties to a new polygon area boundary.
 *
 * @param props Component properties containing polygon coordinates, save handler, and cancellation callback.
 * @returns Rendered JSX modal form element.
 */
const AreaFormModal: React.FC<Props> = ({ coordinates, onSave, onCancel }) => {
  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState<number>(0);
  const [type, setType] = useState<AreaType>(AreaType.GENERIC);
  const [priority, setPriority] = useState<Priority>(Priority.MEDIUM);

  /**
   * Packages the form inputs into an AreaDTO object with GeoJSON Polygon geometry and triggers onSave.
   *
   * @param e Standard React form submission event.
   */
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave({
      name,
      capacity,
      type,
      priority,
      boundary: {
        type: 'Polygon',
        coordinates
      }
    });
  };

  return (
    <Modal title="Nuova Area" onClose={onCancel}>
      <form onSubmit={handleSubmit}>
        <div className="modal-body">
          <div className="mb-3">
            <label className="form-label">Nome Area</label>
            <input type="text" className="form-control" required value={name} onChange={e => setName(e.target.value)} />
          </div>
          <div className="mb-3">
            <label className="form-label">Capacità</label>
            <input type="number" className="form-control" required min="0" value={capacity} onChange={e => setCapacity(parseInt(e.target.value))} />
          </div>
          <div className="mb-3">
            <label className="form-label">Tipologia (Type)</label>
            <select className="form-select" value={type} onChange={e => setType(e.target.value as AreaType)}>
              {Object.values(AreaType).map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div className="mb-3">
            <label className="form-label">Priorità</label>
            <select className="form-select" value={priority} onChange={e => setPriority(e.target.value as Priority)}>
              {Object.values(Priority).map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onCancel}>Annulla</button>
          <button type="submit" className="btn btn-primary">Salva Area</button>
        </div>
      </form>
    </Modal>
  );
};

export default AreaFormModal;
