#!/bin/bash

echo -e "\033[1;31m========================================================\033[0m"
echo -e "\033[1;31m[STOP] Arresto di tutto il sistema in corso...\033[0m"
echo -e "\033[1;31m========================================================\033[0m"

echo "1/3: Uccisione di eventuali processi rimasti appesi (port-forward, simulatore)..."
# Killa tutti i port-forward di kubectl
pkill -f "kubectl port-forward" || true
# Killa la dashboard
pkill -f "minikube dashboard" || true
# Killa il simulatore python se ancora in esecuzione
pkill -f "python -m simulator.src.main" || true

echo "2/4: Eliminazione dei deployment event-analysis..."
kubectl delete deployment -l app=event-analysis --context edge-cluster --ignore-not-found || true

echo "3/4: Arresto del cluster Minikube (edge-cluster)..."
minikube stop -p edge-cluster

echo "4/4: Pulizia completata."
echo -e "\033[1;32mTutto è stato fermato correttamente!\033[0m"
