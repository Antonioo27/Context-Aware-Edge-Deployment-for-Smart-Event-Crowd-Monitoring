#!/bin/bash
set -e

# Genera un tag univoco con il timestamp Unix
TAG=$(date +%s)
IMAGE_NAME="event-management:${TAG}"

echo "========================================="
echo " AVVIO REDEPLOY AUTOMATICO (Tag: ${TAG})"
echo "========================================="

# 1. Compila il JAR Java
echo "1/4 Compilazione del microservizio Spring Boot..."
./gradlew bootJar

# 2. Bild dell'immagine Docker su Minikube
echo "2/4 Build dell'immagine dentro Minikube multi-nodo..."
minikube image build -t ${IMAGE_NAME} . -p edge-cluster

# 3. Aggiorna il manifest YAML o assegna l'immagine al Deployment
echo "3/4 Applicazione manifest YAML e aggiornamento immagine su K8s..."
kubectl apply -f ../k8s/

# Aggiorna il container con la nuova immagine con timestamp
kubectl set image deployment/event-management event-management=${IMAGE_NAME}

# 4. Attesa del Rollout
echo "4/4 Attesa del riavvio del Pod..."
kubectl rollout status deployment/event-management --timeout=90s

echo "========================================="
echo " DEPLOYMENT COMPLETATO CON SUCCESSO!"
echo "========================================="