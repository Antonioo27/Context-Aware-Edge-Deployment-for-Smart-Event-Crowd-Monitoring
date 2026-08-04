# Event Management

Il modulo `eventManagement` funge da **Backend Centrale e Orchestratore** all'interno dell'architettura basata su Kubernetes. 

## Ruolo e Responsabilità
1. **Gestione Dati (CRUD):** Espone le API REST per la gestione delle aree dell'evento. Salva le configurazioni, i confini geografici e la capacità massima di ogni area all'interno del database centrale **PostGIS**.
2. **Orchestrazione Edge (Kubernetes):** Questo è il cuore dinamico del sistema. Quando un utente crea una nuova area tramite API, `eventManagement` agisce come un controller Kubernetes utilizzando il client Java **Fabric8**. 
   - Legge un template YAML (`analysis-template.yaml`).
   - Sostituisce i placeholder inserendo il nome dell'area originale (come variabile d'ambiente) e il nome sanificato per rispettare lo standard di Kubernetes.
   - Crea un nuovo **Deployment** su Kubernetes in tempo reale.
   - Allo stesso modo, quando un'area viene eliminata, si occupa di distruggere il relativo Deployment, terminando così i pod associati in modo pulito.

## Integrazione con Kubernetes
Per permettere all'applicazione di gestire dinamicamente altre risorse all'interno del cluster (creare e cancellare pod/deployment), il pod di `eventManagement` gira con un **ServiceAccount** dedicato (`event-management-sa`).
Tramite le regole RBAC (`Role` e `RoleBinding` definiti nei manifest), l'applicazione ha i permessi ristretti al solo namespace di default per gestire le risorse `deployments`, `replicasets`, `pods` e `services`.

## Comunicazione
- Interroga **PostGIS** per salvare e recuperare la configurazione delle aree.
- Fornisce un endpoint REST (es. `/api/event/area/{areaId}/capacity`) che i pod di analisi chiamano all'avvio per auto-configurarsi.
