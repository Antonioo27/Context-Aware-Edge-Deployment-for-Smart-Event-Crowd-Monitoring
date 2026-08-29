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
  onRefreshNeeded?: () => void;
}

const InfrastructurePanel: React.FC<InfrastructurePanelProps> = ({ nodes, onRefreshNeeded }) => {
  const [migrations, setMigrations] = useState<MigrationDTO[]>([]);
  const [allocations, setAllocations] = useState<Record<string, string[]>>({});
  const [cpuMetrics, setCpuMetrics] = useState<Record<string, number>>({});
  const [distances, setDistances] = useState<NodeDistanceDTO[]>([]);
  const [loadingNode, setLoadingNode] = useState<string | null>(null);
  const [activeStressNodes, setActiveStressNodes] = useState<Set<string>>(new Set());
  const [cordonedNodes, setCordonedNodes] = useState<Set<string>>(new Set());

  const fetchInfrastructureData = async () => {
    try {
      const [migs, allocs, cpus, dists, simStatus] = await Promise.all([
        migrationApi.getAllMigrations().catch(() => []),
        nodeApi.getNodeAllocations().catch(() => ({})),
        nodeApi.getNodeCpuMetrics().catch(() => ({})),
        fetchClient<NodeDistanceDTO[]>('/api/nodes/distances', { method: 'GET' }).catch(() => []),
        nodeApi.getSimulationStatus().catch(() => ({ stressedNodes: [], cordonedNodes: [] }))
      ]);
      
      setMigrations(migs);
      setAllocations(allocs);
      setCpuMetrics(cpus);
      setDistances(dists);

      // Sincronizza lo stato reale da Kubernetes (mantiene la memoria anche dopo F5)
      if (simStatus) {
        setActiveStressNodes(new Set(simStatus.stressedNodes));
        setCordonedNodes(new Set(simStatus.cordonedNodes));
      }
    } catch (e) {
      console.error('Errore nel recupero dati infrastruttura', e);
    }
  };

  const handleToggleStress = async (nodeId: string) => {
    setLoadingNode(nodeId);
    try {
      if (activeStressNodes.has(nodeId)) {
        await nodeApi.stopCpuStress(nodeId);
        setActiveStressNodes((prev) => {
          const next = new Set(prev);
          next.delete(nodeId);
          return next;
        });
      } else {
        await nodeApi.startCpuStress(nodeId, 180);
        setActiveStressNodes((prev) => new Set(prev).add(nodeId));
      }
      if (onRefreshNeeded) onRefreshNeeded();
      await fetchInfrastructureData();
    } catch (err) {
      console.error('Errore esecuzione stress test sul nodo', err);
      alert(`Impossibile modificare lo stress test sul nodo ${nodeId}`);
    } finally {
      setLoadingNode(null);
    }
  };

  // Gestione Spegnimento / Guasto Nodo (kubectl cordon / uncordon)
  const handleToggleCordon = async (nodeId: string) => {
    setLoadingNode(nodeId);
    const isCurrentlyCordoned = cordonedNodes.has(nodeId);
    try {
      await nodeApi.toggleCordonNode(nodeId, !isCurrentlyCordoned);
      setCordonedNodes((prev) => {
        const next = new Set(prev);
        if (isCurrentlyCordoned) {
          next.delete(nodeId);
        } else {
          next.add(nodeId);
        }
        return next;
      });
      if (onRefreshNeeded) onRefreshNeeded();
      await fetchInfrastructureData();
    } catch (err) {
      console.error('Errore durante cordon/uncordon del nodo', err);
      alert(`Impossibile modificare lo stato operativo del nodo ${nodeId}`);
    } finally {
      setLoadingNode(null);
    }
  };
  useEffect(() => {
    fetchInfrastructureData();
    const interval = setInterval(fetchInfrastructureData, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleClearMigrations = async () => {
    if (confirm('Vuoi davvero cancellare lo storico delle migrazioni?')) {
      await migrationApi.deleteAllMigrations();
      fetchInfrastructureData();
    }
  };

  // Helper per estrarre il nome pulito dell'Area dal nome del Pod di K8s
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
      {/* Colonna Sinistra: Nodi, Pod Allocati, CPU Load e Simulazione Sovraccarico */}
      <div className="col-md-5">
        <Card className="h-100">
          <CardHeader className="bg-secondary text-white d-flex justify-content-between align-items-center">
            <h6 className="mb-0 fw-bold">Infrastruttura Nodi & Pod Allocati</h6>
          </CardHeader>
          <CardBody className="p-3">
            <div className="row g-2">
              {nodes.map(node => {
                const isEdge = node.type === 'EDGE';
                const nodeId = node.id || node.name;
                const hostedPods = allocations[nodeId] || [];
                const cpuPercent = cpuMetrics[nodeId] ?? 0;
                const isStressed = activeStressNodes.has(nodeId);
                const isCordoned = cordonedNodes.has(nodeId);

                return (
                  <div key={nodeId} className="col-12">
                    <div className={`p-2 border rounded ${
                      isStressed 
                        ? 'border-danger bg-danger-subtle' 
                        : isEdge 
                          ? 'border-primary-subtle bg-light' 
                          : 'border-info-subtle bg-white'
                    }`}>
                      <div className="d-flex justify-content-between align-items-center mb-1">
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
                            const latencyStr = getPodLatency(podName, nodeId);
                            return (
                              <span 
                                key={podName} 
                                className="badge bg-white text-dark border d-inline-flex align-items-center gap-1 py-1 px-2 shadow-sm"
                                title={`Latenza stimata PostGIS (${displayAreaName} -> ${nodeId}): ${latencyStr}`}
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

                      {/* Bottoni di Simulazione (Solo per Nodi Edge) */}
                        {isEdge && (
                          <div className="d-flex gap-2 mt-2">
                            {/* 1. Pulsante Sovraccarico CPU */}
                            <button
                              className={`btn btn-sm flex-fill fw-semibold d-flex align-items-center justify-content-center gap-1 ${
                                isStressed ? 'btn-danger text-white' : 'btn-outline-danger'
                              }`}
                              onClick={() => handleToggleStress(nodeId)}
                              disabled={loadingNode === nodeId || isCordoned}
                              style={{ fontSize: '0.75rem' }}
                              title="Lancia pod stress-ng per saturare la CPU"
                            >
                              {loadingNode === nodeId && isStressed ? (
                                '...'
                              ) : isStressed ? (
                                'Ferma Stress'
                              ) : (
                                'Sovraccarico (95%)'
                              )}
                            </button>

                            {/* 2. Pulsante Spegni / Cordon Nodo */}
                            <button
                              className={`btn btn-sm flex-fill fw-semibold d-flex align-items-center justify-content-center gap-1 ${
                                isCordoned ? 'btn-success text-white' : 'btn-outline-dark'
                              }`}
                              onClick={() => handleToggleCordon(nodeId)}
                              disabled={loadingNode === nodeId}
                              style={{ fontSize: '0.75rem' }}
                              title="Imposta il nodo su Cordon / Uncordon in Kubernetes"
                            >
                              {loadingNode === nodeId && !isStressed ? (
                                '...'
                              ) : isCordoned ? (
                                'Riattiva Nodo'
                              ) : (
                                'Spegni Nodo'
                              )}
                            </button>
                          </div>
                        )}
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