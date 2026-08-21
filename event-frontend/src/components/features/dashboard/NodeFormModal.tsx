import { useState } from 'react';
import type { NodeDTO } from '../../../types';
import { Modal } from '../../ui/Modal';

interface Props {
  latitude: number;
  longitude: number;
  onSave: (node: NodeDTO) => void;
  onCancel: () => void;
}

const NodeFormModal: React.FC<Props> = ({ latitude, longitude, onSave, onCancel }) => {
  const [id, setId] = useState('');
  const [name, setName] = useState('');
  const [type, setType] = useState('EDGE'); // CLOUD, EDGE, etc.
  const [brokerUrl, setBrokerUrl] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave({
      id: id || undefined,
      name,
      type,
      brokerUrl,
      latitude,
      longitude
    });
  };

  return (
    <Modal title="Nuovo Nodo" onClose={onCancel}>
      <form onSubmit={handleSubmit}>
              <div className="modal-body">
                <div className="mb-3">
                  <label className="form-label">ID Nodo (Opzionale)</label>
                  <input type="text" className="form-control" value={id} onChange={e => setId(e.target.value)} />
                </div>
                <div className="mb-3">
                  <label className="form-label">Nome Nodo</label>
                  <input type="text" className="form-control" required value={name} onChange={e => setName(e.target.value)} />
                </div>
                <div className="mb-3">
                  <label className="form-label">Tipologia (es. CLOUD, EDGE)</label>
                  <select className="form-select" value={type} onChange={e => setType(e.target.value)}>
                    <option value="EDGE">EDGE</option>
                    <option value="CLOUD">CLOUD</option>
                  </select>
                </div>
                <div className="mb-3">
                  <label className="form-label">Broker URL</label>
                  <input type="text" className="form-control" required value={brokerUrl} onChange={e => setBrokerUrl(e.target.value)} placeholder="tcp://localhost:1883" />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={onCancel}>Annulla</button>
                <button type="submit" className="btn btn-primary">Salva Nodo</button>
              </div>
      </form>
    </Modal>
  );
};

export default NodeFormModal;
