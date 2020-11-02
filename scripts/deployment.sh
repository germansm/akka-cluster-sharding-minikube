#!/usr/bin/env bash
# https://doc.akka.io/docs/akka-management/current/bootstrap/recipes.html
kubectl delete namespaces ksmti
kubectl create namespace ksmti
kubectl config set-context $(kubectl config current-context) --namespace=ksmti
kubectl apply -f scripts/cluster.yaml