import React, { useEffect, useState } from 'react';
import { UserType } from '../../../types';
import type { ManualAlertDTO } from '../../../types';
import { alertApi } from '../../../api/alertApi';
import { useAlerts } from '../../../hooks/useAlerts';
import { Card, CardHeader, CardBody } from '../../ui/Card';

interface AlertsSidebarProps {
  manualAlertMode?: boolean;
  setManualAlertMode?: (mode: boolean) => void;
  manualAlertLocation?: {lat: number, lng: number} | null;
  setManualAlertLocation?: (loc: {lat: number, lng: number} | null) => void;
}

const AlertsSidebar: React.FC<AlertsSidebarProps> = ({
  manualAlertMode,
  setManualAlertMode,
  manualAlertLocation,
  setManualAlertLocation
}) => {
  const [selectedUserType, setSelectedUserType] = useState<UserType>(UserType.USER);
  const { alerts, loading, refreshAlerts } = useAlerts(selectedUserType);
  
  const [cause, setCause] = useState('');
  const [lon, setLon] = useState<number | ''>('');
  const [lat, setLat] = useState<number | ''>('');
  const [sendingAlert, setSendingAlert] = useState(false);

  // Sync coords from map
  useEffect(() => {
    if (manualAlertLocation) {
      setLat(manualAlertLocation.lat);
      setLon(manualAlertLocation.lng);
    }
  }, [manualAlertLocation]);

  const handleSendManualAlert = async () => {
    if (!cause || lat === '' || lon === '') {
      alert("Compila tutti i campi per l'alert manuale!");
      return;
    }
    
    setSendingAlert(true);
    try {
      const dto: ManualAlertDTO = {
        ts: new Date().toISOString(),
        cause: cause,
        lat: Number(lat),
        lon: Number(lon)
      };
      await alertApi.sendManualAlert(dto);
      alert("Alert inviato con successo!");
      setCause('');
      setLat('');
      setLon('');
      if (setManualAlertLocation) setManualAlertLocation(null);
      refreshAlerts(); // Optionally refresh alerts after sending
    } catch (e) {
      alert("Errore invio alert");
    } finally {
      setSendingAlert(false);
    }
  };

  return (
    <Card className="h-100" testId="alerts-sidebar">
      <CardHeader className="bg-danger text-white">
        <h5 className="mb-0">Alert e Notifiche</h5>
      </CardHeader>
      <CardBody className="d-flex flex-column p-0" style={{ height: '700px' }}>
        
        <div className="p-3 border-bottom">
          <label htmlFor="userTypeSelect" className="form-label fw-bold">Seleziona Tipo Utente:</label>
          <select 
            id="userTypeSelect" 
            className="form-select" 
            value={selectedUserType} 
            onChange={(e) => setSelectedUserType(e.target.value as UserType)}
            data-testid="user-type-select"
          >
            {Object.values(UserType).map(ut => (
              <option key={ut} value={ut}>{ut}</option>
            ))}
          </select>
        </div>
        
        <div className="flex-grow-1 overflow-auto p-3">
          {loading && alerts.length === 0 ? (
            <p className="text-muted text-center mt-3">Caricamento...</p>
          ) : alerts.length === 0 ? (
            <p className="text-muted text-center mt-3">Nessun alert per questo utente.</p>
          ) : (
            <ul className="list-group list-group-flush">
              {alerts.map((notify, index) => (
                <li key={index} className="list-group-item border-start border-4 border-danger mb-2 bg-light rounded shadow-sm">
                  <div className="fw-bold mb-1">{notify.priority} - {notify.alert?.cause || "System"}</div>
                  <small className="text-muted">{new Date(notify.alert?.ts).toLocaleString()}</small>
                  <p className="mb-0 mt-1 small">{notify.message}</p>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Nuova Sezione per Alert Manuale */}
        <div className="p-3 bg-light border-top">
          <h6 className="fw-bold mb-2">Invia Alert Manuale</h6>
          <div className="mb-2">
            <textarea 
              className="form-control form-control-sm" 
              placeholder="Messaggio / Causa dell'alert" 
              rows={2}
              value={cause}
              onChange={(e) => setCause(e.target.value)}
              data-testid="manual-alert-cause"
            />
          </div>
          
          <div className="row g-2 align-items-center mb-2">
            <div className="col">
              <input type="number" step="any" className="form-control form-control-sm" placeholder="Lat" value={lat} onChange={(e) => setLat(e.target.value === '' ? '' : Number(e.target.value))} data-testid="manual-alert-lat" />
            </div>
            <div className="col">
              <input type="number" step="any" className="form-control form-control-sm" placeholder="Lon" value={lon} onChange={(e) => setLon(e.target.value === '' ? '' : Number(e.target.value))} data-testid="manual-alert-lon" />
            </div>
          </div>

          <div className="d-flex justify-content-between align-items-center mt-2">
            <button 
              className={`btn btn-sm ${manualAlertMode ? 'btn-warning' : 'btn-outline-secondary'}`}
              onClick={() => setManualAlertMode && setManualAlertMode(!manualAlertMode)}
              title="Seleziona dalla mappa"
              data-testid="manual-alert-map-button"
            >
              📍 Mappa
            </button>
            <button 
              className="btn btn-danger btn-sm" 
              onClick={handleSendManualAlert}
              disabled={sendingAlert}
              data-testid="manual-alert-send-button"
            >
              {sendingAlert ? 'Invio...' : 'Invia Alert'}
            </button>
          </div>
          {manualAlertMode && <small className="text-warning d-block mt-1 fw-bold">Clicca sulla mappa per catturare le coordinate.</small>}
        </div>

      </CardBody>
    </Card>
  );
};

export default AlertsSidebar;
