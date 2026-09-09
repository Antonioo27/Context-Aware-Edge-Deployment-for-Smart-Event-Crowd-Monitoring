#!/bin/bash
set -e

TAG=$(date +%s)
IMAGE_NAME="event-management:${TAG}"

echo "========================================="
echo " AVVIO REDEPLOY EVENT-MANAGEMENT (Tag: ${TAG})"
echo "========================================="

# 1. Compilazione JAR
echo "1/4 Compilazione del microservizio Spring Boot..."
./gradlew bootJar

# 2. Build con Tag Univoco dentro Minikube
echo "2/4 Build dell'immagine ${IMAGE_NAME} dentro Minikube..."
minikube image build -t ${IMAGE_NAME} . -p edge-cluster

# 3. Assegnazione alias latest per evitare problemi con i manifest
minikube image tag ${IMAGE_NAME} event-management:latest -p edge-cluster

# 4. Applicazione configurazioni K8s e aggiornamento immagine con timestamp
echo "3/4 Aggiornamento immagine su K8s con tag univoco..."
kubectl apply -f ../k8s/
kubectl set image deployment/event-management event-management=${IMAGE_NAME}

# 5. Attesa rollout
echo "4/4 Attesa del riavvio del Pod..."
kubectl rollout status deployment/event-management --timeout=90s

echo "========================================="
echo " DEPLOYMENT COMPLETATO CON SUCCESSO!"
echo "========================================="