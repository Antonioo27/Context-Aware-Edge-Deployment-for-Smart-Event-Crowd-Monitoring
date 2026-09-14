#!/bin/bash
# Questo script automatizza l'avvio del cluster, dei broker e del simulatore
# unendo le novità introdotte in start_cluster.sh con i redeploy e i port-forward completi.

set -e

PROFILE="edge-cluster"
NODES=4
ENABLE_MOSQUITTO_PF=false

# Parsing argomenti
for arg in "$@"; do
    if [ "$arg" == "--pf" ]; then
        ENABLE_MOSQUITTO_PF=true
    fi
done

print_step() {
    echo -e "\n\033[1;32m========================================================\033[0m"
    echo -e "\033[1;32m[STEP] $1\033[0m"
    echo -e "\033[1;32m========================================================\033[0m"
}

echo "==============================================================="
echo "AVVIO E SINCRONIZZAZIONE AMBIENTE: ${PROFILE} (PF & REDEPLOY)"
echo "==============================================================="

print_step "1/10: Controllo o Creazione del Cluster Minikube"
if minikube status -p ${PROFILE} > /dev/null 2>&1; then
    echo "Cluster Minikube '${PROFILE}' già attivo. Salto la creazione..."
else
    echo "Cluster '${PROFILE}' non attivo. Creazione in corso con ${NODES} nodi..."
    minikube start -p ${PROFILE} --nodes ${NODES}
fi

print_step "2/10: Etichettatura dei nodi"
kubectl label node edge-cluster tier=cloud node-id=node-cloud --overwrite 
kubectl label node edge-cluster-m02 tier=edge node-id=node-edge-1 --overwrite 
kubectl label node edge-cluster-m03 tier=edge node-id=node-edge-2 --overwrite 
kubectl label node edge-cluster-m04 tier=edge node-id=node-edge-3 --overwrite

print_step "3/10: Configurazione Metrics Server (risoluzione 10s)"
minikube addons enable metrics-server -p ${PROFILE}
kubectl patch deployment metrics-server -n kube-system --patch-file k8s/patches/metrics-server-patch.yaml
kubectl rollout status deployment metrics-server -n kube-system --timeout=60s

print_step "4/10: Verifica immagine polinux/stress-ng su Minikube"
if ! minikube image ls -p ${PROFILE} | grep -q "polinux/stress-ng"; then
    echo "Scaricamento e caricamento immagine polinux/stress-ng..."
    docker pull polinux/stress-ng
    minikube image load polinux/stress-ng -p ${PROFILE}
else
    echo "Immagine polinux/stress-ng già presente nella cache dei nodi."
fi

print_step "5/10: Creazione ConfigMap per eventAnalysis"
kubectl create configmap event-analysis-config --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -

print_step "6/10: Applicazione dei Manifest Kubernetes di base"
kubectl apply -f k8s/

print_step "7/10: Esecuzione degli script di redeploy (Build & Aggiornamento Pod)"
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

print_step "8/10: Attesa servizi e avvio tunnel port-forward (Backend e Broker)"
echo "Attesa di event-management-svc..."
while ! kubectl get svc event-management-svc > /dev/null 2>&1; do sleep 2; done


echo "Attesa dei servizi mosquitto..."
while ! kubectl get svc mosquitto-cloud mosquitto-edge-1 mosquitto-edge-2 mosquitto-edge-3 > /dev/null 2>&1; do sleep 2; done


# Kill precedenti port-forward per evitare conflitti (sia backend che mosquitto)
pkill -f "port-forward.*8080" || true
pkill -f "port-forward.*188[3-6]" || true

print_step "9/10: Avvio port forward (in background)"
nohup kubectl port-forward svc/event-management-svc 8080:8080 > /dev/null 2>&1 &
if [ "$ENABLE_MOSQUITTO_PF" = true ]; then
    nohup kubectl port-forward svc/mosquitto-cloud 1883:1883 > /dev/null 2>&1 &
    nohup kubectl port-forward svc/mosquitto-edge-1 1884:1883 > /dev/null 2>&1 &
    nohup kubectl port-forward svc/mosquitto-edge-2 1885:1883 > /dev/null 2>&1 &
    nohup kubectl port-forward svc/mosquitto-edge-3 1886:1883 > /dev/null 2>&1 &
else
    echo "Port forward per Mosquitto disabilitati. Usa il flag --pf per abilitarli."
fi
sleep 2

print_step "10/10: Avvio minikube dashboard (in background)"
minikube dashboard -p edge-cluster &

echo "==============================================================="
echo "✅ INFRASTRUTTURA PRONTA E ALLINEATA!"
echo "   - Nodi configurati: 1 Cloud + 3 Edge"
echo "   - Risoluzione CPU K8s: 10s"
echo "   - Backend attivo su: http://localhost:8080"
if [ "$ENABLE_MOSQUITTO_PF" = true ]; then
    echo "   - Broker Mosquitto esposti (1883-1886)"
fi
echo "==============================================================="
