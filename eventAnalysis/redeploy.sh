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

# Recupera tutti i deployment attivi che iniziano con event-analysis-
DEPLOYMENTS=$(kubectl get deployment -o jsonpath='{.items[*].metadata.name}' | tr ' ' '\n' | grep '^event-analysis-' || true)

if [ -n "$DEPLOYMENTS" ]; then
  for dep in $DEPLOYMENTS; do
    echo "Aggiornamento container 'analysis' per $dep a ${IMAGE_NAME}..."
    kubectl set image deployment/$dep analysis=${IMAGE_NAME}
  done

  echo "Attesa del completamento del rollout..."
  for dep in $DEPLOYMENTS; do
    kubectl rollout status deployment/$dep --timeout=90s
  done
else
  echo "Nessun Pod di analisi attivo al momento. Le nuove aree useranno il nuovo tag."
fi

echo "========================================="
echo " DEPLOYMENT COMPLETATO CON SUCCESSO!"
echo "========================================="