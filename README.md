# Context-Aware Edge Deployment for Smart Event Crowd Monitoring


## 1. Prerequisiti

- Python 3.11+
- Docker e Docker Compose
- `mosquitto-clients` per ispezionare i topic da riga di comando:

```bash
sudo apt install mosquitto-clients
```

## 2. Ambiente Python

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

`requirements.txt` deve contenere almeno:

```
paho-mqtt>=2.0
fastapi
uvicorn
```

Il vincolo su `paho-mqtt>=2.0` non e' negoziabile: il codice usa
`CallbackAPIVersion.VERSION2`, che nella serie 1.x non esiste.

## 3. Avviare il broker

```bash
docker compose up -d broker
docker compose logs -f broker
```

Nei log devi vedere il listener aperto su `1883` e nessun errore di
configurazione. Per fermarlo:

```bash
docker compose down          # ferma il broker, mantiene la persistenza
docker compose down -v       # cancella anche le sessioni persistenti
```

Quel `-v` non e' un dettaglio: azzera le code dei subscriber offline, quindi
usalo fra un esperimento e l'altro per partire da uno stato pulito, e **non**
usarlo in mezzo a un test di persistenza o falsificherai il risultato.

### Perche' serve un file di configurazione

Mosquitto 2.x ha due default che in container bloccano tutto:

- il listener ascolta solo sull'interfaccia interna, quindi dall'host non si
  connette nessuno;
- `allow_anonymous` e' `false`, quindi ogni CONNECT viene rifiutato con
  `rc=5 (not authorised)`.

`deploy/mosquitto/mosquitto.conf` li corregge e attiva la persistenza, che e'
la condizione perche' le sessioni dei subscriber sopravvivano al riavvio del
broker. I commenti nel file spiegano ogni parametro.


ma senza persistenza: le sessioni non sopravvivono al riavvio del container.

## 4. Avviare il simulatore

Con il broker attivo, da radice del repository:

```bash
source .venv/bin/activate
SIM_DURATION_SECONDS=60 python -m simulator.src.main
```


A fine run viene stampato il riepilogo dei contatori e viene scritto
`ground_truth.csv`.

### Osservare il traffico

In un altro terminale, prima di far partire il simulatore:

```bash
# tutto
mosquitto_sub -h localhost -t 'event/#' -v

# solo i probe di un'area
mosquitto_sub -h localhost -t 'event/probes/stage' -v

# solo lo stato del simulatore (retained: arriva subito)
mosquitto_sub -h localhost -t 'event/status/#' -v
```

Cosa ti aspetti di vedere: prima un messaggio di stato `online`, poi sei topic
`evento/probes/<area>` che pubblicano con `batch_id` crescente e senza buchi.



## 5. Topic

| Topic | Chi pubblica | Contenuto |
|---|---|---|
| `event/probes/<area>` | simulatore | Batch di probe grezzi |
| `event/analysis/<area>` | servizio di analisi | Stima, livello di affollamento, trend |
| `event/alerts/<area>` | servizio di analisi | Superamento soglie |
| `event/decisions` | orchestratore | Decisioni di placement motivate |
| `event/status/<componente>` | tutti | Stato online/offline (retained + Last Will) |

Il prefisso `event` e' un contratto fra quattro componenti che non si parlano
direttamente: se lo cambi, va cambiato ovunque.

Formato del batch di probe:

```json
{
  "area_id": "stage",
  "sensor_id": "ap-stage-01",
  "batch_id": 1247,
  "sent_at": "2026-08-03T14:09:13.591+00:00",
  "count": 50,
  "probes": [{"sensor_id": "...", "ts": "...", "mac": "...", "rssi": -67}]
}
```

`sent_at` e' ora di parete UTC, non tempo simulato: serve al ricevente per
misurare la latenza di trasporto contro il proprio orologio. `batch_id` e'
progressivo **per area**, cosi' un buco nella sequenza rende quantificabile la
perdita.

---

## Guida all'Avvio su Kubernetes (Minikube a 4 Nodi)

L'infrastruttura è progettata per essere eseguita in un ambiente distribuito. Puoi emulare questo comportamento in locale sfruttando Minikube configurato in modalità multi-nodo.

### 1. Inizializzazione del Cluster
Avvia Minikube richiedendo esplicitamente la creazione di 4 nodi virtuali:
```bash
minikube start --nodes 4
```
*(Puoi verificare lo stato dei nodi lanciando `kubectl get nodes`)*

### 2. Configurazione dell'ambiente Docker
Poiché abbiamo impostato l'`imagePullPolicy: Never` nei manifest, dobbiamo compilare le immagini Docker *direttamente* all'interno del demone Docker di Minikube, altrimenti i nodi non troveranno le immagini.
```bash
eval $(minikube docker-env)
```

### 3. Build delle Immagini Locali
Mantenendo attivo il terminale precedente, procedi con la compilazione e la build delle immagini per entrambi i microservizi:

**Event Management (Backend & Orchestratore):**
```bash
cd eventManagement
./gradlew bootJar
docker build -t event-management:latest .
cd ..
```

**Event Analysis (Edge Worker):**
```bash
cd eventAnalysis
./gradlew bootJar
docker build -t event-analysis:latest .
cd ..
```

### 4. Deploy dell'Infrastruttura di Base
L'applicazione di Analisi verrà creata dinamicamente. Noi dobbiamo far partire unicamente il Database (PostGIS), il broker messaggi (Mosquitto) e l'orchestratore (Event Management):
```bash
kubectl apply -f k8s/postgis.yaml
kubectl apply -f k8s/mosquitto.yaml
kubectl apply -f k8s/event-management.yaml
```

### 5. Accesso e Test
Una volta che i pod sono in stato `Running`, possiamo esporre le porte per testare il sistema dal nostro computer host:

**Esponi l'API di backend:**
```bash
minikube service event-management-svc
```
*(Minikube aprirà automaticamente una pagina del browser o stamperà un URL del tipo `http://127.0.0.1:XXXXX` da cui potrai richiamare gli endpoint REST, ad es. su Postman)*

**Esponi il Broker MQTT (per il simulatore):**
Se il simulatore di sensori python gira sul tuo PC e non dentro K8s, necessita di parlare con Mosquitto. Usa il port-forwarding (sarà da cambiare con una configurazione più solida):
```bash
kubectl port-forward svc/mosquitto-svc 1883:1883
```
Ora il simulatore potrà inviare i dati a `localhost:1883`.

### (Utility) Aggiornare il codice
Se modifichi il codice Java, ripeti il punto 3 per il servizio interessato e poi forza il riavvio del pod:
```bash
kubectl delete pod -l app=event-management
```
