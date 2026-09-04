#!/bin/bash
set -e

PROFILE="edge-cluster"
NODES=4

echo "==============================================================="
echo "AVVIO E SINCRONIZZAZIONE AMBIENTE: ${PROFILE}"
echo "==============================================================="

# 1. Controllo o Creazione del Cluster Minikube
if minikube status -p ${PROFILE} > /dev/null 2>&1; then
    echo " [1/6] Cluster Minikube '${PROFILE}' già attivo. Salto la creazione..."
else
    echo " [1/6] Cluster '${PROFILE}' non attivo. Creazione in corso con ${NODES} nodi..."
    minikube start -p ${PROFILE} --nodes ${NODES}
fi

# 2. Assegnazione / Riapplicazione Idempotente delle Label
echo " [2/6] Verifica e applicazione etichette ai nodi..."
kubectl label node edge-cluster tier=cloud node-id=node-cloud --overwrite
kubectl label node edge-cluster-m02 tier=edge node-id=node-edge-1 --overwrite
kubectl label node edge-cluster-m03 tier=edge node-id=node-edge-2 --overwrite
kubectl label node edge-cluster-m04 tier=edge node-id=node-edge-3 --overwrite

# 3. Abilitazione e Tuning del Metrics Server (Risoluzione a 10s con timeout 5s)
echo " [3/6] Configurazione Metrics Server (risoluzione 10s)..."
minikube addons enable metrics-server -p ${PROFILE}

# Applica la patch dal file versionato
kubectl patch deployment metrics-server -n kube-system --patch-file k8s/patches/metrics-server-patch.yaml

kubectl rollout status deployment metrics-server -n kube-system --timeout=60s

# 4. Precaricamento Immagine Stress-ng (azzeramento ContainerCreating al click)
echo " [4/6] Verifica immagine polinux/stress-ng su Minikube..."
if ! minikube image ls -p ${PROFILE} | grep -q "polinux/stress-ng"; then
    echo "       Scaricamento e caricamento immagine polinux/stress-ng..."
    docker pull polinux/stress-ng
    minikube image load polinux/stress-ng -p ${PROFILE}
else
    echo "       Immagine polinux/stress-ng già presente nella cache dei nodi."
fi

# 5. Applicazione dei Manifest Kubernetes
echo " [5/6] Distribuzione dei manifest in k8s/..."
kubectl apply -f k8s/

# Attesa avvio componenti core
kubectl rollout status deployment/postgis-db --timeout=90s
kubectl rollout status deployment/mosquitto-cloud --timeout=90s
kubectl rollout status deployment/event-management --timeout=90s

# 6. Ripristino Port-Forwarding Backend
echo " [6/6] Avvio Port-Forwarding porta 8080 in background..."
pkill -f "port-forward.*8080" || true
nohup kubectl port-forward svc/event-management-svc 8080:8080 > /dev/null 2>&1 &
sleep 2

echo "==============================================================="
echo "✅ INFRASTRUTTURA PRONTA E ALLINEATA!"
echo "   - Nodi configurati: 1 Cloud + 3 Edge"
echo "   - Risoluzione CPU K8s: 10s"
echo "   - Backend attivo su: http://localhost:8080"
echo "==============================================================="