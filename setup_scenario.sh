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

# 4. Creazione Aree con Stage Ridotto (~2200 m^2) e Capienze Ricalibrate
echo "Creazione Aree (e avvio Pod di analisi)..."

# ENTRATA (Settore Nord-Ovest)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "entrata", "capacity": 1800, "priority": "HIGH", "type": "ENTRANCE", "boundary": {"type": "Polygon", "coordinates": [[[11.3424915, 44.4947998], [11.3424433, 44.4946161], [11.3428884, 44.4945166], [11.3428991, 44.4946773], [11.3424915, 44.4947998]]]}}' $API_URL/event/area > /dev/null
sleep 1

# CORRIDOIO (Settore Centro-Nord)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "corridoio", "capacity": 1500, "priority": "MEDIUM", "type": "TRANSIT", "boundary": {"type": "Polygon", "coordinates": [[[11.3428133, 44.4945243], [11.3427489, 44.4940460], [11.3426041, 44.4940460], [11.3426363, 44.4945128], [11.3428133, 44.4945243]]]}}' $API_URL/event/area > /dev/null
sleep 1

# STAGE COMPATTO (Settore Sud - ~50m x 44m)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "stage", "capacity": 3000, "priority": "VERY_HIGH", "type": "PEAK_ATTRACTION", "boundary": {"type": "Polygon", "coordinates": [[[11.3427000, 44.4936500], [11.3427000, 44.4932500], [11.3433500, 44.4932500], [11.3433500, 44.4936500], [11.3427000, 44.4936500]]]}}' $API_URL/event/area > /dev/null
sleep 1

# BAGNO (Settore Ovest)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "bagno", "capacity": 250, "priority": "MEDIUM", "type": "GENERIC", "boundary": {"type": "Polygon", "coordinates": [[[11.3425130, 44.4937016], [11.3426256, 44.4939044], [11.3424486, 44.4939809], [11.3423414, 44.4936748], [11.3425130, 44.4937016]]]}}' $API_URL/event/area > /dev/null
sleep 1

# FOOD (Settore Est)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "food", "capacity": 1400, "priority": "HIGH", "type": "SUSTAINED_ATTRACTION", "boundary": {"type": "Polygon", "coordinates": [[[11.3436070, 44.4941187], [11.3434729, 44.4938240], [11.3437625, 44.4937590], [11.3438215, 44.4940881], [11.3436070, 44.4941187]]]}}' $API_URL/event/area > /dev/null
sleep 1

# USCITA (Settore Nord-Est)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "uscita", "capacity": 1800, "priority": "HIGH", "type": "EXIT", "boundary": {"type": "Polygon", "coordinates": [[[11.3435319, 44.4945434], [11.3435158, 44.4944324], [11.3437786, 44.4943789], [11.3438108, 44.4945090], [11.3435319, 44.4945434]]]}}' $API_URL/event/area > /dev/null
sleep 1

# INFOPOINT (Settore Centro-Est)
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "infopoint", "capacity": 150, "priority": "LOW", "type": "GENERIC", "boundary": {"type": "Polygon", "coordinates": [[[11.3429366, 44.4940421], [11.3429205, 44.4939388], [11.3432745, 44.4938852], [11.3432906, 44.4939733], [11.3429366, 44.4940421]]]}}' $API_URL/event/area > /dev/null
sleep 1

echo "Configurazione completata con successo!"