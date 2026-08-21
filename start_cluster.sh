#!/bin/bash

# Questo script automatizza l'avvio del cluster, dei broker e del simulatore
# come descritto nel file k8s_commands.md

# Termina lo script se un comando fallisce
set -e

# Funzione per mostrare lo stato di avanzamento in modo visibile
print_step() {
    echo -e "\n\033[1;32m========================================================\033[0m"
    echo -e "\033[1;32m[STEP] $1\033[0m"
    echo -e "\033[1;32m========================================================\033[0m"
}


print_step "1/7: Avvio del cluster minikube"
minikube start -p edge-cluster --nodes 4

print_step "2/7: Etichettatura dei nodi"
kubectl label node edge-cluster tier=cloud node-id=node-cloud --overwrite 
kubectl label node edge-cluster-m02 tier=edge node-id=node-edge-1 --overwrite 
kubectl label node edge-cluster-m03 tier=edge node-id=node-edge-2 --overwrite 
kubectl label node edge-cluster-m04 tier=edge node-id=node-edge-3 --overwrite

print_step "3/8: Creazione ConfigMap"
kubectl create configmap event-analysis-config --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -

print_step "4/8: Esecuzione degli script di redeploy"
# Visto che i redeploy.sh si trovano nelle sottocartelle, li richiamo singolarmente
if [ -f "eventManagement/redeploy.sh" ]; then
    echo "-> Eseguo eventManagement/redeploy.sh"
    cd eventManagement
    chmod +x redeploy.sh
    ./redeploy.sh
    cd ..
else
    echo "-> ATTENZIONE: eventManagement/redeploy.sh non trovato!"
fi

if [ -f "eventAnalysis/redeploy.sh" ]; then
    echo "-> Eseguo eventAnalysis/redeploy.sh"
    cd eventAnalysis
    chmod +x redeploy.sh
    ./redeploy.sh
    cd ..
else
    echo "-> ATTENZIONE: eventAnalysis/redeploy.sh non trovato!"
fi

print_step "5/8: Attesa dei servizi per i port-forward"
# Aspetta che event-management-svc sia pronto per il port-forward
echo "Attesa di event-management-svc..."
while ! kubectl get svc event-management-svc > /dev/null 2>&1; do
    sleep 2
done

print_step "6/8: Avvio port forward per event-management-svc (in background)"
kubectl port-forward svc/event-management-svc 8080:8080 &

print_step "7/8: Avvio minikube dashboard (in background)"
minikube dashboard -p edge-cluster &

print_step "8/8: Attesa servizi mosquitto e avvio tunnel broker"
echo "Attesa dei servizi mosquitto..."
while ! kubectl get svc mosquitto-cloud mosquitto-edge-1 mosquitto-edge-2 mosquitto-edge-3 > /dev/null 2>&1; do
    sleep 2
done

# Avvio dei vari tunnel broker in background
kubectl port-forward svc/mosquitto-cloud 1883:1883 &
kubectl port-forward svc/mosquitto-edge-1 1884:1883 &
kubectl port-forward svc/mosquitto-edge-2 1885:1883 &
kubectl port-forward svc/mosquitto-edge-3 1886:1883 &
