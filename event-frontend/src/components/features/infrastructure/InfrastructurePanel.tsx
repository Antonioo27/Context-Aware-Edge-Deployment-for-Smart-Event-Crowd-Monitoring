import React, { useEffect, useState } from 'react';
import type { NodeDTO, MigrationDTO } from '../../../types';
import { migrationApi } from '../../../api/migrationApi';
import { nodeApi } from '../../../api/nodeApi';
import fetchClient from '../../../api/client';
import { Card, CardHeader, CardBody } from '../../ui/Card';

interface NodeDistanceDTO {
  areaName: string;
  nodeId: string;
  distanceMeters: number;
  estimatedIngressLatencyMs: number;
}

interface InfrastructurePanelProps {
  nodes: NodeDTO[];
//  onRefreshNeeded?: () => void;
}

//const InfrastructurePanel: React.FC<InfrastructurePanelProps> = ({ nodes, onRefreshNeeded }) => {
const InfrastructurePanel: React.FC<InfrastructurePanelProps> = ({ nodes }) => {
  const [migrations, setMigrations] = useState<MigrationDTO[]>([]);
  const [allocations, setAllocations] = useState<Record<string, string[]>>({});
  const [cpuMetrics, setCpuMetrics] = useState<Record<string, number>>({});
  const [distances, setDistances] = useState<NodeDistanceDTO[]>([]);
//  const [syncingK8s, setSyncingK8s] = useState<boolean>(false);

  const fetchInfrastructureData = async () => {
    try {
      const [migs, allocs, cpus, dists] = await Promise.all([
        migrationApi.getAllMigrations().catch(() => []),
        nodeApi.getNodeAllocations().catch(() => ({})),
        nodeApi.getNodeCpuMetrics().catch(() => ({})),
        fetchClient<NodeDistanceDTO[]>('/api/nodes/distances', { method: 'GET' }).catch(() => [])
      ]);
      setMigrations(migs);
      setAllocations(allocs);
      setCpuMetrics(cpus);
      setDistances(dists);
    } catch (e) {
      console.error('Errore nel recupero dati infrastruttura', e);
    }
  };

  useEffect(() => {
    fetchInfrastructureData();
    const interval = setInterval(fetchInfrastructureData, 4000);
    return () => clearInterval(interval);
  }, []);

//  const handleSyncK8s = async () => {
//    setSyncingK8s(true);
//    try {
//      await nodeApi.syncK8sNodes();
//      if (onRefreshNeeded) onRefreshNeeded();
//      await fetchInfrastructureData();
//    } catch (e) {
//      console.error('Errore sync K8s', e);
//      alert('Errore durante la sincronizzazione con Kubernetes');
//    } finally {
//      setSyncingK8s(false);
//    }
//  };

  const handleClearMigrations = async () => {
    if (confirm('Vuoi davvero cancellare lo storico delle migrazioni?')) {
      await migrationApi.deleteAllMigrations();
      fetchInfrastructureData();
    }
  };

  // Helper per estrarre il nome pulito dell'Area dal nome del Pod di K8s
  // Es: "event-analysis-stage-699ffbb9f6-tpg8d" -> "stage"
  const extractAreaName = (podName: string): string => {
    return podName
      .replace(/^event-analysis-/, '')
      .replace(/-[a-z0-9]{8,10}-[a-z0-9]{5}$/, '');
  };

  // Helper per recuperare la latenza stimata tra Area e Nodo
  const getPodLatency = (podNameOrArea: string, nodeId: string): string => {
    const cleanArea = extractAreaName(podNameOrArea);
    
    const entry = distances.find(d => 
      (d.areaName.toLowerCase() === cleanArea.toLowerCase() || 
      podNameOrArea.includes(`event-analysis-${d.areaName}`)) && 
      d.nodeId === nodeId
    );

    if (!entry) {
      return nodeId.toLowerCase().includes('cloud') ? '40.0 ms' : '~1.0 ms';
    }
    
    return `${entry.estimatedIngressLatencyMs.toFixed(1)} ms`;
  };

  const getCpuBadgeColor = (cpuPercent: number) => {
    if (cpuPercent >= 75) return 'bg-danger text-white';
    if (cpuPercent >= 50) return 'bg-warning text-dark';
    return 'bg-success-subtle text-success border border-success-subtle';
  };

  return (
    <div className="row g-3">
      {/* Colonna Sinistra: Nodi, Pod Allocati e CPU Load */}
      <div className="col-md-5">
        <Card className="h-100">
          <CardHeader className="bg-secondary text-white d-flex justify-content-between align-items-center">
            <h6 className="mb-0 fw-bold">Infrastruttura Nodi & Pod Allocati</h6>
          </CardHeader>
          <CardBody className="p-3">
            <div className="row g-2">
              {nodes.map(node => {
                const isEdge = node.type === 'EDGE';
                const hostedPods = allocations[node.id || ''] || [];
                const cpuPercent = cpuMetrics[node.id || ''] ?? 0;

                return (
                  <div key={node.id || node.name} className="col-12">
                    <div className={`p-2 border rounded ${isEdge ? 'border-primary-subtle bg-light' : 'border-info-subtle bg-white'}`}>
                      <div className="d-flex justify-content-between align-items-center mb-1">
                        {/* Intestazione Nodo Pulita (Senza ID duplicato) */}
                        <div className="d-flex align-items-center gap-2">
                          <strong className="text-dark">{node.name}</strong>
                          <span className={`badge ${isEdge ? 'bg-primary' : 'bg-dark'}`}>{node.type}</span>
                        </div>
                        
                        {/* Carico CPU */}
                        <div className="d-flex align-items-center gap-1">
                          <span className="small text-muted">CPU:</span>
                          <span className={`badge ${getCpuBadgeColor(cpuPercent)}`}>
                            {cpuPercent.toFixed(1)}%
                          </span>
                        </div>
                      </div>

                      {/* Barra di avanzamento CPU */}
                      <div className="progress mb-2" style={{ height: '4px' }}>
                        <div 
                          className={`progress-bar ${cpuPercent >= 75 ? 'bg-danger' : cpuPercent >= 50 ? 'bg-warning' : 'bg-success'}`} 
                          role="progressbar" 
                          style={{ width: `${Math.min(100, cpuPercent)}%` }} 
                        />
                      </div>

                      <div className="text-muted small mb-1">
                        <code>{node.brokerUrl}</code> | GPS: [{node.latitude.toFixed(4)}, {node.longitude.toFixed(4)}]
                      </div>

                      {/* Pod Allocati con Latenza di Ingresso Reale da PostGIS */}
                      <div className="d-flex align-items-center gap-1 flex-wrap mt-2">
                        <span className="small text-secondary me-1">Pod attivi ({hostedPods.length}):</span>
                        {hostedPods.length === 0 ? (
                          <span className="text-muted small fst-italic">Nessun Pod</span>
                        ) : (
                          hostedPods.map(podName => {
                            const displayAreaName = extractAreaName(podName);
                            const latencyStr = getPodLatency(podName, node.id || '');
                            return (
                              <span 
                                key={podName} 
                                className="badge bg-white text-dark border d-inline-flex align-items-center gap-1 py-1 px-2 shadow-sm"
                                title={`Latenza stimata PostGIS (${displayAreaName} -> ${node.id}): ${latencyStr}`}
                              >
                                <span className="text-primary fw-bold">{displayAreaName}</span>
                                <span className="badge bg-info-subtle text-info-emphasis rounded-pill" style={{ fontSize: '0.75rem' }}>
                                  {latencyStr}
                                </span>
                              </span>
                            );
                          })
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

      {/* Colonna Destra: Audit Log Migrazioni */}
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