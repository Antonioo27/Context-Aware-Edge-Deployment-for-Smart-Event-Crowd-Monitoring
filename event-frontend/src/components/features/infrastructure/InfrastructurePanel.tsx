import React, { useEffect, useState } from 'react';
import type { NodeDTO, MigrationDTO } from '../../../types';
import { migrationApi } from '../../../api/migrationApi';
import { nodeApi } from '../../../api/nodeApi';
import { Card, CardHeader, CardBody } from '../../ui/Card';

interface InfrastructurePanelProps {
  nodes: NodeDTO[];
  onRefreshNeeded?: () => void;
}

const InfrastructurePanel: React.FC<InfrastructurePanelProps> = ({ nodes, onRefreshNeeded }) => {
  const [migrations, setMigrations] = useState<MigrationDTO[]>([]);
  const [allocations, setAllocations] = useState<Record<string, string[]>>({});
  const [syncingK8s, setSyncingK8s] = useState<boolean>(false);
  
  const fetchAllocationsAndMigrations = async () => {
    try {
      const [migs, allocs] = await Promise.all([
        migrationApi.getAllMigrations().catch(() => []),
        nodeApi.getNodeAllocations().catch(() => ({}))
      ]);
      setMigrations(migs);
      setAllocations(allocs);
    } catch (e) {
      console.error('Errore nel recupero dati infrastruttura', e);
    }
  };

  useEffect(() => {
    fetchAllocationsAndMigrations();
    const interval = setInterval(fetchAllocationsAndMigrations, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleSyncK8s = async () => {
    setSyncingK8s(true);
    try {
      await nodeApi.syncK8sNodes();
      if (onRefreshNeeded) onRefreshNeeded();
      await fetchAllocationsAndMigrations();
    } catch (e) {
      console.error('Errore sync K8s', e);
      alert('Errore durante la sincronizzazione con Kubernetes');
    } finally {
      setSyncingK8s(false);
    }
  };

  const handleClearMigrations = async () => {
    if (confirm('Vuoi davvero cancellare lo storico delle migrazioni?')) {
      await migrationApi.deleteAllMigrations();
      fetchAllocationsAndMigrations();
    }
  };

  return (
    <div className="row g-3">
      {/* Colonna Sinistra: Nodi e Pod Allocati (Espansa al 100% senza scrollbar) */}
      <div className="col-md-5">
        <Card className="h-100">
          <CardHeader className="bg-secondary text-white d-flex justify-content-between align-items-center">
            <h6 className="mb-0 fw-bold">Infrastruttura Nodi & Pod Allocati</h6>
            <button 
              className="btn btn-sm btn-light py-0 px-2" 
              onClick={handleSyncK8s} 
              disabled={syncingK8s}
              title="Sincronizza stato da Kubernetes"
            >
              {syncingK8s ? 'Syncing...' : 'Sync K8s'}
            </button>
          </CardHeader>
          <CardBody className="p-3">
            <div className="row g-2">
              {nodes.map(node => {
                const isEdge = node.type === 'EDGE';
                const hostedPods = allocations[node.id || ''] || [];

                return (
                  <div key={node.id || node.name} className="col-12">
                    <div className={`p-2 border rounded ${isEdge ? 'border-primary-subtle bg-light' : 'border-info-subtle bg-white'}`}>
                      <div className="d-flex justify-content-between align-items-center mb-1">
                        <strong>{node.name} <span className="text-muted small">({node.id})</span></strong>
                        <span className={`badge ${isEdge ? 'bg-primary' : 'bg-dark'}`}>{node.type}</span>
                      </div>
                      <div className="text-muted small mb-1">
                        <code>{node.brokerUrl}</code> | GPS: [{node.latitude.toFixed(4)}, {node.longitude.toFixed(4)}]
                      </div>
                      <div className="d-flex align-items-center gap-1 flex-wrap mt-1">
                        <span className="small text-secondary me-1">Pod attivi ({hostedPods.length}):</span>
                        {hostedPods.length === 0 ? (
                          <span className="text-muted small fst-italic">Nessun Pod</span>
                        ) : (
                          hostedPods.map(podName => (
                            <span key={podName} className="badge bg-success-subtle text-success border border-success-subtle">
                              {podName.replace('event-analysis-', '')}
                            </span>
                          ))
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </CardBody>
        </Card>
      </div>

      {/* Colonna Destra: Audit Log Migrazioni Orchestratore */}
      <div className="col-md-7">
        <Card className="h-100 d-flex flex-column">
          <CardHeader className="bg-dark text-white d-flex justify-content-between align-items-center">
            <div className="d-flex align-items-center gap-2">
              <h6 className="mb-0 fw-bold">Audit Log Migrazioni K8s</h6>
              <span className="badge bg-secondary">{migrations.length} eventi</span>
            </div>
            {migrations.length > 0 && (
              <button className="btn btn-outline-light btn-sm py-0 px-2" onClick={handleClearMigrations}>
                Reset Log
              </button>
            )}
          </CardHeader>
          <CardBody className="p-0 flex-grow-1 overflow-auto">
            {migrations.length === 0 ? (
              <div className="text-center py-4 text-muted small">Nessuna migrazione registrata.</div>
            ) : (
              <table className="table table-sm table-hover mb-0" style={{ fontSize: '0.85rem' }}>
                <thead className="table-light sticky-top">
                  <tr>
                    <th>Ora</th>
                    <th>Area</th>
                    <th>Tratta (From → To)</th>
                    <th>Delta Costo</th>
                    <th>Motivo</th>
                    <th>Stato</th>
                  </tr>
                </thead>
                <tbody>
                  {migrations.map(m => (
                    <tr key={m.id}>
                      <td className="text-nowrap">{new Date(m.timestamp).toLocaleTimeString()}</td>
                      <td><span className="fw-bold">{m.areaId}</span></td>
                      <td className="text-nowrap">
                        <code>{m.fromNode}</code> → <code>{m.toNode}</code>
                      </td>
                      <td className="text-nowrap">
                        {m.previousCost != null && m.newCost != null ? (
                          <span className={m.newCost < m.previousCost ? 'text-success' : 'text-danger'}>
                            {m.previousCost.toFixed(1)} → {m.newCost.toFixed(1)}
                          </span>
                        ) : '-'}
                      </td>
                      <td className="text-truncate" style={{ maxWidth: '220px' }} title={m.reason}>
                        {m.reason}
                      </td>
                      <td>
                        {m.success ? (
                          <span className="badge bg-success">OK</span>
                        ) : (
                          <span className="badge bg-danger" title={m.errorMessage || 'Errore'}>FAIL</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </CardBody>
        </Card>
      </div>
    </div>
  );
};

export default InfrastructurePanel;