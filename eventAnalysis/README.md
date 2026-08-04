# Event Analysis

Il modulo `eventAnalysis` rappresenta il **nodo Edge Computing / Worker** della architettura.

## Ruolo e Responsabilità
A differenza del backend centrale, questo servizio non è pensato per essere statico. **Viene instanziato dinamicamente** dall'orchestratore (`eventManagement`) ogni volta che viene creata una nuova area di monitoraggio. 
Ogni pod di `eventAnalysis` è dedicato esclusivamente al calcolo e all'aggregazione dei dati (folla, alert, anomalie) per una *singola area specifica*.

## Flusso di Funzionamento
1. **Auto-Configurazione:** Al suo avvio su Kubernetes, il pod legge la variabile d'ambiente `AREA_ID` iniettata dall'orchestratore (il nome originale dell'area).
2. **Interrogazione del Backend:** Utilizza `EventManagementClient` per fare una richiesta HTTP al backend (`http://event-management-svc:8080`) e scaricare le specifiche dell'area di sua competenza (ad esempio la `capacity` massima consentita). L'uso del DNS interno di K8s (`event-management-svc`) permette una comunicazione affidabile a prescindere dall'IP del nodo.
3. **Ascolto Dati (MQTT):** Si iscrive al broker Mosquitto sul topic specifico della sua area (es. `event/probes/[AREA_ID]`) tramite Spring Integration MQTT, elaborando solo i log dei sensori di sua pertinenza.
4. **Analisi:** [WORK IN PROGRESS] Utilizzando il database (PostGIS) o comunicando a sua volta con il broker, calcolerà e notificherà metriche sul sovraffollamento o alert predittivi.

## Vantaggi su Kubernetes
Questo design *One-Pod-per-Area* massimizza la scalabilità: all'aumentare delle aree dell'evento, aumentano parallelamente i pod di elaborazione. Kubernetes può distribuire questi pod in maniera uniforme sui vari nodi fisici del cluster, garantendo alte prestazioni e tolleranza ai guasti (se un nodo Edge cade, K8s sposterà il pod di analisi su un altro nodo funzionante).
