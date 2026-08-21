import React from 'react';
import AlertsSidebar from '../alerts/AlertsSidebar';
import AnalysisSidebar from '../analysis/AnalysisSidebar';
import MonitoringMap from '../map/MonitoringMap';
import AreaFormModal from './AreaFormModal';
import NodeFormModal from './NodeFormModal';
import { useDashboardData } from '../../../hooks/useDashboardData';
import { useMapInteractions } from '../../../hooks/useMapInteractions';
import { Card, CardHeader, CardBody } from '../../ui/Card';

const Dashboard: React.FC = () => {
  const { areas, nodes, refreshData } = useDashboardData();
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

  return (
    <div className="container-fluid mt-4" data-testid="dashboard-container">
      <div className="row g-3">
        {/* Colonna Sinistra: Alert */}
        <div className="col-md-3">
          <AlertsSidebar 
            manualAlertMode={manualAlertMode}
            setManualAlertMode={setManualAlertMode}
            manualAlertLocation={manualAlertLocation}
            setManualAlertLocation={setManualAlertLocation}
          />
        </div>

        {/* Colonna Centrale: Mappa */}
        <div className="col-md-6">
          <Card className="h-100">
            <CardHeader className="bg-dark text-white d-flex justify-content-between align-items-center">
              <h5 className="mb-0">Mappa di Monitoraggio</h5>
              <div className="d-flex align-items-center gap-2 small">
                <span className="badge border" style={{backgroundColor: 'transparent', color: 'white'}}>NONE</span>
                <span className="badge bg-success">LOW</span>
                <span className="badge bg-warning" style={{color: 'white'}}>MEDIUM</span>
                <span className="badge" style={{backgroundColor: 'orange', color: 'white'}}>HIGH</span>
                <span className="badge bg-danger">CRITICAL</span>
              </div>
            </CardHeader>
            <CardBody className="p-0 position-relative">
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

        {/* Colonna Destra: Analisi */}
        <div className="col-md-3">
          <AnalysisSidebar areas={areas} />
        </div>
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
          onSave={handleSaveNode}
          onCancel={handleCancel}
        />
      )}
    </div>
  );
};

export default Dashboard;
