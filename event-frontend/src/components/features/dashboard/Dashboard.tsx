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

  // Chiamata esplicita al sync K8s dal frontend
  const handleSyncK8s = async () => {
    setSyncing(true);
    try {
      const syncedNodes = await nodeApi.syncK8sNodes();
      alert(`Sincronizzazione completata con successo! Rilevati ${syncedNodes.length} nodi da Kubernetes.`);
      await refreshData();
    } catch (err) {
      console.error('Errore durante la sincronizzazione con Kubernetes', err);
      alert('Errore durante la sincronizzazione dei nodi da K8s.');
    } finally {
      setSyncing(false);
    }
  };

  useEffect(() => {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission().then((permission) => {
        console.log(`Permesso notifiche browser: ${permission}`);
      });
    }
  }, []);

  useMqttAlerts(nodes);

  return (
    <div className="container-fluid mt-2" data-testid="dashboard-container">
      {/* 3 Colonne: Alert | Mappa | Analisi */}
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

      {/* Pannello Nodi e Migrazioni sottostante */}
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