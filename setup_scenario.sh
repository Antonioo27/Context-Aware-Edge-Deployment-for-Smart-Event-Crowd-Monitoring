#!/bin/bash
set -e
API_URL="http://192.168.58.2:30080/api"
echo "Configuring environment..."

# 1. Sincronizzazione dei nodi K8s
echo "Sincronizzazione nodi Kubernetes..."
curl -s -X POST $API_URL/nodes/sync-k8s > /dev/null
sleep 2

# 2. Posizionamento Equidistante dei Nodi Edge intorno a Piazza Maggiore
echo "Configurazione posizioni dei nodi..."
# Nord-Ovest (Nettuno)
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-edge-1", "name": "Nodo EDGE (node-edge-1)", "type": "EDGE", "brokerUrl": "tcp://192.168.58.3:1883", "latitude": 44.494400, "longitude": 11.342200}' $API_URL/nodes/node-edge-1 > /dev/null

# Sud (San Petronio)
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-edge-2", "name": "Nodo EDGE (node-edge-2)", "type": "EDGE", "brokerUrl": "tcp://192.168.58.4:1883", "latitude": 44.493000, "longitude": 11.342800}' $API_URL/nodes/node-edge-2 > /dev/null

# Est (Palazzo dei Banchi)
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-edge-3", "name": "Nodo EDGE (node-edge-3)", "type": "EDGE", "brokerUrl": "tcp://192.168.58.5:1883", "latitude": 44.493800, "longitude": 11.344100}' $API_URL/nodes/node-edge-3 > /dev/null

# Cloud (Remoto)
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-cloud", "name": "Nodo CLOUD (node-cloud)", "type": "CLOUD", "brokerUrl": "tcp://192.168.58.2:1883", "latitude": 45.338852, "longitude": 9.462438}' $API_URL/nodes/node-cloud > /dev/null
sleep 2

# 3. Creazione Evento
echo "Creazione Evento..."
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "Sagra delle Pappardelle", "description": "Sono molte buone, venite a mangiare! Di coniglio e di cinghiale.", "location": "Piazza Maggiore", "city": "Bologna"}' $API_URL/event > /dev/null
sleep 2

# 4. Creazione Aree con Coordinate PostGIS, Capienze e Priorità Ricalibrate
echo "Creazione Aree (e avvio Pod di analisi)..."

# ENTRATA (capacity: 2000, priority: HIGH)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "entrata", "capacity": 2000, "priority": "HIGH", "type": "ENTRANCE", "boundary": {"type": "Polygon", "coordinates": [[[11.342574044511371, 44.493568672290294], [11.3425579423807, 44.493339076102714], [11.343164455969575, 44.493289330142936], [11.3431376190851, 44.49346152751525], [11.342574044511371, 44.493568672290294]]]}}' $API_URL/event/area > /dev/null
sleep 1

# CORRIDOIO (capacity: 2000, priority: MEDIUM)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "corridoio", "capacity": 2000, "priority": "MEDIUM", "type": "TRANSIT", "boundary": {"type": "Polygon", "coordinates": [[[11.34253647287313, 44.49432250675002], [11.342386186320168, 44.49367964345695], [11.342568677134507, 44.49363372437911], [11.34271896368747, 44.494307200563505], [11.34253647287313, 44.49432250675002]]]}}' $API_URL/event/area > /dev/null
sleep 1

# STAGE (capacity: 4000, priority: VERY_HIGH)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "stage", "capacity": 4000, "priority": "VERY_HIGH", "type": "PEAK_ATTRACTION", "boundary": {"type": "Polygon", "coordinates": [[[11.342761902702613, 44.493966636874156], [11.342633085657223, 44.49359163185933], [11.343454294321774, 44.493438567895], [11.343518702844447, 44.49383270678853], [11.342761902702613, 44.493966636874156]]]}}' $API_URL/event/area > /dev/null
sleep 1

# STAND (capacity: 1500, priority: LOW)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "stand", "capacity": 1500, "priority": "LOW", "type": "SUSTAINED_ATTRACTION", "boundary": {"type": "Polygon", "coordinates": [[[11.342783372210187, 44.49425745542955], [11.342751167948848, 44.49401638225619], [11.3431376190851, 44.49393602431027], [11.34321276236158, 44.494169444703985], [11.342783372210187, 44.49425745542955]]]}}' $API_URL/event/area > /dev/null
sleep 1

# FOOD (capacity: 1500, priority: LOW)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "food", "capacity": 1500, "priority": "LOW", "type": "SUSTAINED_ATTRACTION", "boundary": {"type": "Polygon", "coordinates": [[[11.343250333999821, 44.49418475092666], [11.34317519072334, 44.49393985088164], [11.343507968090682, 44.493870972558675], [11.343636785136113, 44.49413883224656], [11.343250333999821, 44.49418475092666]]]}}' $API_URL/event/area > /dev/null
sleep 1

# USCITA (capacity: 2000, priority: MEDIUM)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "uscita", "capacity": 2000, "priority": "MEDIUM", "type": "EXIT", "boundary": {"type": "Polygon", "coordinates": [[[11.343717295789496, 44.4941426588046], [11.343599213497832, 44.49379444099329], [11.343819275950452, 44.49367964345695], [11.343931990865174, 44.49411204633313], [11.343717295789496, 44.4941426588046]]]}}' $API_URL/event/area > /dev/null
sleep 1

echo "Configurazione completata con successo!"