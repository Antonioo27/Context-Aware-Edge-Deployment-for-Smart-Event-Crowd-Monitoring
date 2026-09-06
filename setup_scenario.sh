#!/bin/bash
set -e
API_URL="http://localhost:8080/api"
echo "Configuring environment..."

# 1. Sincronizzazione dei nodi K8s
echo "Sincronizzazione nodi Kubernetes..."
curl -s -X POST $API_URL/nodes/sync-k8s > /dev/null
sleep 2

# 2. Posizionamento dei nodi
echo "Configurazione posizioni dei nodi..."
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-edge-1", "name": "Nodo EDGE (node-edge-1)", "type": "EDGE", "brokerUrl": "tcp://192.168.58.3:1883", "latitude": 44.4942180539051, "longitude": 11.338744222831899}' $API_URL/nodes/node-edge-1 > /dev/null
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-edge-2", "name": "Nodo EDGE (node-edge-2)", "type": "EDGE", "brokerUrl": "tcp://192.168.58.4:1883", "latitude": 44.49254199961361, "longitude": 11.342466002019497}' $API_URL/nodes/node-edge-2 > /dev/null
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-edge-3", "name": "Nodo EDGE (node-edge-3)", "type": "EDGE", "brokerUrl": "tcp://192.168.58.5:1883", "latitude": 44.49359049583919, "longitude": 11.344911436183104}' $API_URL/nodes/node-edge-3 > /dev/null
curl -s -X PUT -H 'Content-Type: application/json' -d '{"id": "node-cloud", "name": "Nodo CLOUD (node-cloud)", "type": "CLOUD", "brokerUrl": "tcp://192.168.58.2:1883", "latitude": 45.33885264696802, "longitude": 9.462438396878278}' $API_URL/nodes/node-cloud > /dev/null
sleep 2

# 3. Creazione Evento
echo "Creazione Evento..."
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "Sagra delle Pappardelle", "description": "Sono molte buone, venite a mangiare! Di coniglio e di cinghiale.", "location": "Piazza Maggiore", "city": "Bologna"}' $API_URL/event > /dev/null
sleep 2

# 4. Creazione Aree
echo "Creazione Aree (e avvio dei Pod di analisi)..."
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "bagno", "capacity": 100, "priority": "MEDIUM", "type": "GENERIC", "boundary": {"type": "Polygon", "coordinates": [[[11.342513017514673, 44.49370162354025], [11.342625636205728, 44.493904432309385], [11.342448663976896, 44.493980963737066], [11.342341408080651, 44.49367483742367], [11.342513017514673, 44.49370162354025]]]}}' $API_URL/event/area > /dev/null
sleep 1
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "food", "capacity": 500, "priority": "HIGH", "type": "SUSTAINED_ATTRACTION", "boundary": {"type": "Polygon", "coordinates": [[[11.343607027656471, 44.49411872005382], [11.343472957786187, 44.4938240742022], [11.34376254870607, 44.49375902232005], [11.343821539449003, 44.49408810756714], [11.343607027656471, 44.49411872005382]]]}}' $API_URL/event/area > /dev/null
sleep 1
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "stage", "capacity": 1600, "priority": "VERY_HIGH", "type": "PEAK_ATTRACTION", "boundary": {"type": "Polygon", "coordinates": [[[11.342684626948698, 44.49395035117804], [11.342523743104291, 44.49362509174596], [11.343639204425362, 44.493276870813844], [11.343741097526797, 44.493705450127315], [11.342684626948698, 44.49395035117804]]]}}' $API_URL/event/area > /dev/null
sleep 1
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "uscita", "capacity": 400, "priority": "HIGH", "type": "EXIT", "boundary": {"type": "Polygon", "coordinates": [[[11.343531948529117, 44.4945434666487], [11.343515860144652, 44.494432497116144], [11.343778637090496, 44.494378925542115], [11.343810813859385, 44.494509027850846], [11.343531948529117, 44.4945434666487]]]}}' $API_URL/event/area > /dev/null
sleep 1
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "corridoio", "capacity": 350, "priority": "MEDIUM", "type": "TRANSIT", "boundary": {"type": "Polygon", "coordinates": [[[11.342813334024175, 44.49452433398573], [11.342748980486435, 44.494046015371644], [11.342604185026495, 44.494046015371644], [11.342636361795384, 44.494512854384936], [11.342813334024175, 44.49452433398573]]]}}' $API_URL/event/area > /dev/null
sleep 1
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "entrata", "capacity": 350, "priority": "HIGH", "type": "ENTRANCE", "boundary": {"type": "Polygon", "coordinates": [[[11.3424915663354, 44.49479984372673], [11.342443301182087, 44.494616170710714], [11.34288841315157, 44.49451668091879], [11.342899138741187, 44.49467739511367], [11.3424915663354, 44.49479984372673]]]}}' $API_URL/event/area > /dev/null
sleep 1
curl -s -X POST -H 'Content-Type: application/json' -d '{"name": "infopoint", "capacity": 50, "priority": "LOW", "type": "GENERIC", "boundary": {"type": "Polygon", "coordinates": [[[11.342936678304882, 44.49404218880692], [11.34292058992046, 44.49393887146428], [11.34327453437808, 44.493885299436755], [11.343290622762545, 44.49397331059881], [11.342936678304882, 44.49404218880692]]]}}' $API_URL/event/area > /dev/null
sleep 1

echo "Configurazione completata con successo!"
