/**
 * Main operations dashboard view component.
 * Integrates GIS monitoring, real-time metrics telemetry, notification alerts,
 * crowd regression charts, and Kubernetes edge/cloud infrastructure controls into a unified layout.
 */

import React, { useState, useEffect } from 'react';
import AlertsSidebar from '../alerts/AlertsSidebar';
import AnalysisSidebar from '../analysis/AnalysisSidebar';
import MonitoringMap from '../map/MonitoringMap';
import InfrastructurePanel from '../infrastructure/InfrastructurePanel';
import AreaFormModal from './AreaFormModal';
import NodeFormModal from './NodeFormModal';
import { useDashboardData } from '../../../hooks/useDashboardData';
import { useMapInteractions } from '../../../hooks/useMapInteractions';
import { nodeApi } from '../../../api/nodeApi';
import { Card, CardHeader, CardBody } from '../../ui/Card';
import { useMqttAlerts } from '../../../hooks/useMqttAlerts';
import MetricsSummary from '../metrics/MetricsSummary';

/**
 * Top-level dashboard component coordinating state between map drawing tools,
 * real-time telemetry updates, MQTT fast-path listeners, and backend synchronization.
 *
 * @returns Rendered JSX dashboard component.
 */
const Dashboard: React.FC = () => {
  const { areas, nodes, refreshData } = useDashboardData();
  const [syncing, setSyncing] = useState<boolean>(false);

  const {
    showAreaModal,
    showNodeModal,
    currentAreaCoords,
    currentNodeCoords,
    manualAlertMode,
    setManualAlertMode,
    manualAlertLocation,
    setManualAlertLocation,
    handleAreaDraw,
    handleNodeDraw,
    handleAreaEdit,
    handleNodeEdit,
    handleAreaDelete,
    handleSaveArea,
    handleSaveNode,
    handleCancel
  } = useMapInteractions(refreshData);

  /**
   * Triggers an explicit Kubernetes node discovery and synchronization workflow,
   * importing cluster worker topology into the Event Management service.
   */
  const handleSyncK8s = async () => {
    setSyncing(true);
    try {
      const syncedNodes = await nodeApi.syncK8sNodes();
      alert(`Synchronization successful! Discovered ${syncedNodes.length} nodes from Kubernetes.`);
      await refreshData();
    } catch (err) {
      console.error('Error synchronizing nodes with Kubernetes:', err);
      alert('Error during Kubernetes node synchronization.');
    } finally {
      setSyncing(false);
    }
  };

  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission().then((permission) => {
        console.log(`Browser notification permission: ${permission}`);
      });
    }
  }, []);

  useMqttAlerts(nodes);

  return (
    <div className="container-fluid mt-2" data-testid="dashboard-container">
      <MetricsSummary />
    
      <div className="row g-3">
        <div className="col-md-3">
          <AlertsSidebar 
            manualAlertMode={manualAlertMode}
            setManualAlertMode={setManualAlertMode}
            manualAlertLocation={manualAlertLocation}
            setManualAlertLocation={setManualAlertLocation}
          />
        </div>

        <div className="col-md-6">
          <Card className="h-100 d-flex flex-column">
            <CardHeader className="bg-dark text-white d-flex justify-content-between align-items-center">
              <div className="d-flex align-items-center gap-2">
                <h5 className="mb-0">Mappa di Monitoraggio</h5>
                <button 
                  className="btn btn-sm btn-outline-info text-white py-0 px-2"
                  onClick={handleSyncK8s}
                  disabled={syncing}
                >
                  {syncing ? 'Sincronizzazione...' : '🔄 Sincronizza Nodi K8s'}
                </button>
              </div>
              <div className="d-flex align-items-center gap-2 small">
                <span className="badge border" style={{backgroundColor: 'transparent', color: 'white'}}>NONE</span>
                <span className="badge bg-success">LOW</span>
                <span className="badge bg-warning" style={{color: 'white'}}>MEDIUM</span>
                <span className="badge" style={{backgroundColor: 'orange', color: 'white'}}>HIGH</span>
                <span className="badge bg-danger">CRITICAL</span>
              </div>
            </CardHeader>
            <CardBody className="p-0 position-relative flex-grow-1 d-flex flex-column" style={{ minHeight: '700px' }}>
              <MonitoringMap 
                areas={areas}
                nodes={nodes}
                onAreaDraw={handleAreaDraw}
                onNodeDraw={handleNodeDraw}
                onAreaEdit={handleAreaEdit}
                onNodeEdit={handleNodeEdit}
                onAreaDelete={handleAreaDelete}
                manualAlertMode={manualAlertMode}
                onMapClick={(lat, lng) => {
                  setManualAlertLocation({ lat, lng });
                  setManualAlertMode(false);
                }}
              />
            </CardBody>
          </Card>
        </div>

        <div className="col-md-3">
          <AnalysisSidebar areas={areas} />
        </div>
      </div>

      <div className="mt-3">
        <InfrastructurePanel 
          nodes={nodes} 
        />
      </div>

      {showAreaModal && currentAreaCoords && (
        <AreaFormModal
          coordinates={currentAreaCoords}
          onSave={handleSaveArea}
          onCancel={handleCancel}
        />
      )}
      
      {showNodeModal && currentNodeCoords && (
        <NodeFormModal
          latitude={currentNodeCoords.lat}
          longitude={currentNodeCoords.lng}
          nodes={nodes}
          onSave={handleSaveNode}
          onCancel={handleCancel}
        />
      )}
    </div>
  );
};

export default Dashboard;