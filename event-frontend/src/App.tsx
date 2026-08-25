import { useEffect, useState } from 'react'
import CreateEvent from './components/features/event/CreateEvent'
import Dashboard from './components/features/dashboard/Dashboard'
import PolicySelector from './components/features/policy/PolicySelector'
import { eventApi } from './api/eventApi'
import type { Event } from './types'

function App() {
  const [loading, setLoading] = useState(true)
  const [eventData, setEventData] = useState<Event | null>(null)

  const checkEvent = async () => {
    setLoading(true);
    try {
      const data = await eventApi.getEvent();
      setEventData(data);
    } catch (err) {
      console.log("Nessun evento trovato, mostro schermata di creazione");
      setEventData(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkEvent();
  }, []);

  const handleSkipToDashboard = () => {
    setEventData({
      id: 'active',
      name: 'Smart Event Monitoring',
      description: 'Sessione di monitoraggio attiva',
      location: 'Area Evento',
      city: 'Bologna'
    });
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Caricamento...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="container-fluid py-3">
      <header className="pb-3 mb-4 border-bottom d-flex justify-content-between align-items-center flex-wrap gap-2">
        <div className="d-flex align-items-center text-dark text-decoration-none">
          <span className="fs-4 fw-bold">
            {eventData ? `Pannello di Controllo: ${eventData.name}` : "Event Management Dashboard"}
          </span>
        </div>

        {eventData && (
          <div className="d-flex align-items-center gap-3">
            {/* Menu a tendina per la politica dell'orchestratore */}
            <PolicySelector />

            <button 
              className="btn btn-outline-danger btn-sm" 
              onClick={async () => {
                if (confirm('Vuoi davvero eliminare questo evento e resettare tutto?')) {
                  try {
                    await eventApi.deleteEvent();
                  } catch (e) {
                    console.error(e);
                  }
                  setEventData(null);
                }
              }}
            >
              Elimina / Cambia Evento
            </button>
          </div>
        )}
      </header>

      <main>
        {eventData ? (
          <div className="row align-items-md-stretch">
            <div className="col-md-12 mb-2 px-4">
              <p className="lead">{eventData.description} - <strong>{eventData.location}, {eventData.city}</strong></p>
            </div>
            <div className="col-md-12">
              <Dashboard />
            </div>
          </div>
        ) : (
          <CreateEvent 
            onEventCreated={checkEvent} 
            onSkipToDashboard={handleSkipToDashboard} 
          />
        )}
      </main>

      <footer className="pt-2 mt-2 text-muted border-top text-center" style={{ fontSize: '0.85rem' }}>
        &copy; {new Date().getFullYear()} Context-Aware Edge Deployment
      </footer>
    </div>
  )
}

export default App