#!/bin/bash
set -e

# Genera un tag univoco con il timestamp Unix
TAG=$(date +%s)
IMAGE_NAME="event-analysis:${TAG}"

echo "========================================="
echo " AVVIO REDEPLOY EVENT-ANALYSIS (Tag: ${TAG})"
echo "========================================="

# 1. Compilazione JAR Spring Boot
echo "1/4 Compilazione microservizio Spring Boot..."
./gradlew bootJar

# 2. Build dell'immagine Docker locale (tag timestamp + tag latest)
echo "2/4 Build dell'immagine Docker locale..."
docker build -t ${IMAGE_NAME} -t event-analysis:latest .

# 3. Caricamento di entrambi i tag su TUTTI i nodi di Minikube
echo "3/4 Caricamento immagini su tutti i nodi di Minikube..."
minikube image load ${IMAGE_NAME} -p edge-cluster
minikube image load event-analysis:latest -p edge-cluster

# 4. Aggiornamento immagine sui Deployment di analisi attivi
echo "4/4 Aggiornamento dei Pod di analisi attivi..."
if kubectl get deployment -l app=event-analysis 2>/dev/null | grep -q event-analysis; then
  kubectl set image deployment -l app=event-analysis analysis=${IMAGE_NAME}
  kubectl rollout status deployment -l app=event-analysis --timeout=90s
else
  echo "Nessun Pod di analisi attivo al momento. Le nuove aree usera' il nuovo tag."
fi

echo "========================================="
echo " DEPLOYMENT COMPLETATO CON SUCCESSO!"
echo "========================================="