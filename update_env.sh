#!/bin/bash
set -e

echo "================================================="
echo " AGGIORNAMENTO PARAMETRI (.env)"
echo "================================================="

if [ ! -f .env ]; then
    echo "Errore: File .env non trovato nella directory corrente!"
    exit 1
fi

echo "1/2 Aggiornamento ConfigMap 'event-analysis-config'..."
kubectl create configmap event-analysis-config --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -

echo "2/2 Riavvio Pod per ricaricare le variabili d'ambiente..."
kubectl rollout restart deployment event-management

DEPLOYMENTS=$(kubectl get deployment -o jsonpath='{.items[*].metadata.name}' | tr ' ' '\n' | grep '^event-analysis-' || true)
if [ -n "$DEPLOYMENTS" ]; then
    for dep in $DEPLOYMENTS; do
        kubectl rollout restart deployment/$dep
    done
fi

echo "Attesa riavvio backend..."
kubectl rollout status deployment/event-management --timeout=90s

echo "================================================="
echo " CONFIGURAZIONE AGGIORNATA CON SUCCESSO!"
echo "================================================="