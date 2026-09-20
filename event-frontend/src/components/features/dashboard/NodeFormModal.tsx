/**
 * Cluster node placement modal dialog component.
 * Allows operators to associate discovered Kubernetes cluster nodes (edge or cloud)
 * with geographic GPS coordinates selected on the monitoring map.
 */

import React, { useState, useEffect } from 'react';
import type { NodeDTO } from '../../../types';
import { Modal } from '../../ui/Modal';

interface Props {
  latitude: number;
  longitude: number;
  nodes: NodeDTO[];
  onSave: (node: NodeDTO) => void;
  onCancel: () => void;
}

/**
 * Renders a modal dialog for selecting a Kubernetes worker/edge node and assigning GPS coordinates.
 * Defaults to selecting the first unpositioned node in the cluster if available.
 *
 * @param props Component properties including clicked coordinates, available nodes, and save/cancel callbacks.
 * @returns Rendered JSX modal dialog element.
 */
const NodeFormModal: React.FC<Props> = ({ latitude, longitude, nodes, onSave, onCancel }) => {
  const unpositionedNodes = nodes.filter(n => n.latitude === 0 && n.longitude === 0);
  const defaultSelectedId = (unpositionedNodes.length > 0 ? unpositionedNodes[0].id : nodes[0]?.id) || '';

  const [selectedNodeId, setSelectedNodeId] = useState<string>(defaultSelectedId);
  const selectedNode = nodes.find(n => n.id === selectedNodeId);

  useEffect(() => {
    if (!selectedNodeId && nodes.length > 0) {
      setSelectedNodeId(nodes[0].id || '');
    }
  }, [nodes, selectedNodeId]);

  /**
   * Updates the selected node's geographic coordinates with the newly chosen latitude and longitude.
   *
   * @param e Standard React form submission event.
   */
  const handleSubmit = (e: React.SubmitEvent) => {
    e.preventDefault();
    if (!selectedNode) return;

    onSave({
      ...selectedNode,
      latitude,
      longitude
    });
  };

  return (
    <Modal title="Posiziona Nodo su Mappa" onClose={onCancel}>
      <form onSubmit={handleSubmit}>
        <div className="modal-body">
          {nodes.length === 0 ? (
            <div className="alert alert-warning mb-0">
              <i className="bi bi-exclamation-triangle me-2"></i>
              Nessun nodo trovato. Esegui prima il <strong>Sync Nodi K8s</strong>.
            </div>
          ) : (
            <>
              <div className="mb-3">
                <label className="form-label fw-bold">Seleziona Nodo K8s da associare:</label>
                <select
                  className="form-select"
                  value={selectedNodeId}
                  onChange={e => setSelectedNodeId(e.target.value)}
                  required
                >
                  {nodes.map(n => {
                    const isAlreadyPlaced = n.latitude !== 0 || n.longitude !== 0;
                    return (
                      <option key={n.id} value={n.id}>
                        {n.name} ({n.id}) - [{n.type}] {isAlreadyPlaced ? '📍 (già posizionato)' : '⚪ (da posizionare)'}
                      </option>
                    );
                  })}
                </select>
                <div className="form-text small">
                  I dati del nodo provengono direttamente dalla discovery del cluster Minikube.
                </div>
              </div>

              {selectedNode && (
                <div className="p-3 bg-light rounded border mb-3">
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <span className="fw-bold text-dark">{selectedNode.name}</span>
                    <span className={`badge ${selectedNode.type === 'EDGE' ? 'bg-primary' : 'bg-dark'}`}>
                      {selectedNode.type}
                    </span>
                  </div>
                  <div className="small text-muted mb-1">
                    <strong>Broker URL:</strong> <code>{selectedNode.brokerUrl}</code>
                  </div>
                  <div className="small text-muted">
                    <strong>Stato attuale:</strong> {selectedNode.latitude === 0 && selectedNode.longitude === 0 ? (
                      <span className="text-warning fw-bold">Non ancora posizionato</span>
                    ) : (
                      <span className="text-success">Posizionato a [{selectedNode.latitude.toFixed(4)}, {selectedNode.longitude.toFixed(4)}]</span>
                    )}
                  </div>
                </div>
              )}

              <div className="row g-2">
                <div className="col-6">
                  <label className="form-label small fw-bold">Latitudine selezionata</label>
                  <input type="text" className="form-control form-control-sm" value={latitude.toFixed(6)} disabled />
                </div>
                <div className="col-6">
                  <label className="form-label small fw-bold">Longitudine selezionata</label>
                  <input type="text" className="form-control form-control-sm" value={longitude.toFixed(6)} disabled />
                </div>
              </div>
            </>
          )}
        </div>

        <div className="modal-footer">
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Annulla
          </button>
          <button type="submit" className="btn btn-primary" disabled={!selectedNode}>
            Salva Posizione Nodo
          </button>
        </div>
      </form>
    </Modal>
  );
};

export default NodeFormModal;