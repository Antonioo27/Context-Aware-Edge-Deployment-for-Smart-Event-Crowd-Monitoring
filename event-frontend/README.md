# Event Frontend - Crowd Monitoring System

Questa applicazione React costituisce il pannello di controllo frontend (dashboard) per il sistema _Context-Aware Edge Deployment for Smart Event Crowd Monitoring_. Permette agli operatori di creare un evento, monitorare le aree di interesse su una mappa interattiva, gestire i nodi edge/cloud, inviare/ricevere alert e visualizzare le analisi (sia storiche che predittive) relative all'affluenza.

Il progetto è costruito utilizzando tecnologie moderne:

- **React 18** (con **Vite**) per un'esperienza utente fluida e reattiva.
- **TypeScript** per garantire la type-safety su tutto il codice.
- **React-Leaflet** per il rendering interattivo della mappa.
- **Recharts** per la visualizzazione dei dati e dei trend sotto forma di grafici.
- **Bootstrap 5** per un layout responsive e pulito.

---

## Architettura e Struttura del Progetto

Il progetto segue una struttura basata su **funzionalità (feature-based)** per garantire manutenibilità, separazione delle responsabilità e scalabilità. Tutta la logica di business e gestione dello stato (come chiamate API e polling) è stata estratta dai componenti visivi ed è gestita da _Custom Hooks_.

La cartella principale `src/` è organizzata come segue:

### `src/components/features/`

Contiene tutti i componenti specifici legati al dominio dell'applicazione, suddivisi per funzionalità:

- **`dashboard/`**
  - `Dashboard.tsx`: È il componente orchestratore principale. Riunisce la mappa, i pannelli laterali (analisi e alert) e utilizza gli hook per passare i dati e le funzioni di callback ai componenti figli.
  - `AreaFormModal.tsx`: Modale per la creazione di nuove aree di monitoraggio (definendo nome, capacità, priorità e tipologia).
  - `NodeFormModal.tsx`: Modale per l'aggiunta di nodi di calcolo (es. Edge o Cloud) inserendo il relativo broker URL.
- **`map/`**
  - `MonitoringMap.tsx`: Gestisce l'istanza di Leaflet (`react-leaflet`). Mostra i poligoni delle aree (colorati dinamicamente in base allo stato di affollamento) e i marker dei nodi. Permette inoltre di tracciare o modificare queste forme grazie a `leaflet-draw`.

- **`alerts/`**
  - `AlertsSidebar.tsx`: Un pannello laterale (a sinistra) che esegue il polling per mostrare gli ultimi alert ricevuti. Permette inolte di passare alla modalità di inserimento manuale di un alert cliccando direttamente sulla mappa.

- **`analysis/`**
  - `AnalysisSidebar.tsx`: Un pannello laterale (a destra) che mostra i dettagli e i grafici (`recharts`) dei trend (storico vs predetto) relativi alle persone stimate e alla densità dell'area selezionata.

- **`event/`**
  - `CreateEvent.tsx`: La schermata iniziale che permette di configurare un nuovo evento, registrando i metadati nel sistema. Finché un evento non è creato, la Dashboard principale non è accessibile.

### `src/components/ui/`

Contiene componenti visivi generici (dumb components) che non hanno alcuna dipendenza con la logica di business:

- **`Card.tsx`**: Wrapper riutilizzabile (header, body) basato sulle card di Bootstrap.
- **`Modal.tsx`**: Wrapper riutilizzabile per gestire il markup (e l'overlay) delle modali Bootstrap.

### `src/hooks/`

Tutta la logica complessa, di fetch e di sincronizzazione con il backend è stata estratta in Custom Hooks per alleggerire i componenti React:

- **`usePolling.ts`**: Un hook generico per eseguire funzioni in modo ricorrente tramite `setInterval`, prevenendo problemi di "stale closures".
- **`useDashboardData.ts`**: Gestisce il fetching iniziale e il polling per il recupero in tempo reale delle `Aree` e dei `Nodi`.
- **`useAlerts.ts`**: Gestisce il ciclo di vita del fetching degli alert dal server.
- **`useMapInteractions.ts`**: Incapsula tutti i complessi gestori di eventi per la mappa (click, disegno, edit e delete dei livelli).

### `src/api/`

Contiene i moduli (pattern "API service") responsabili unicamente delle comunicazioni HTTP tramite `fetch` verso il backend. Organizzati logicamente in `alertApi.ts`, `analysisApi.ts`, `areaApi.ts`, `eventApi.ts` e `nodeApi.ts`.

---

## Esecuzione e Sviluppo

### Prerequisiti

- **Node.js** (versione consigliata 18+).
- Le API backend (o il port-forward di Kubernetes verso i servizi `event-management-svc`, `area-management-svc`, ecc.) devono essere accessibili su `http://localhost:8080`. Se necessario, l'URL base è modificabile in `src/api/config.ts`.

### Avvio Veloce

1. Installare le dipendenze:

   ```bash
   npm install
   ```

2. Eseguire l'applicazione in modalità di sviluppo (con Hot Module Replacement):

   ```bash
   npm run dev
   ```

3. Compilare il bundle di produzione:
   ```bash
   npm run build
   ```

## Workflow Tipico

1. All'avvio dell'app (`App.tsx`), l'utente verifica se esiste un evento attivo. In caso contrario, utilizza il componente `CreateEvent` per crearne uno.
2. Una volta creato l'evento, si sblocca l'accesso alla `Dashboard`.
3. Sulla mappa (`MonitoringMap`), l'utente può disegnare poligoni per creare Aree di monitoraggio.
4. L'utente disegna dei marker sulla mappa per inserire Nodi Edge/Cloud per indicare le capacità di calcolo distribuite.
5. In base ai flussi provenienti dal backend, la mappa cambierà colore (da verde a rosso) ad indicare lo stato dell'affollamento (`NONE`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
6. Cliccando sulle aree disponibili sulla `AnalysisSidebar`, si potranno visualizzare in tempo reale i grafici con lo storico e le previsioni sul flusso dei visitatori.
7. Eventuali problemi scateneranno gli alert, visibili nella lista della `AlertsSidebar`. L'utente può anche lanciare alert manuali geolocalizzati interagendo direttamente con la mappa.
