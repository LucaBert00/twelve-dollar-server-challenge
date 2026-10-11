#!/usr/bin/env bash
# Planned MODE=k8s. A kustomize overlay will deploy the seed Job, app, and Nginx.
# This script will then wait for the rollout and run the shared benchmark tool:
#   python3 benchmark.py --mode k8s --url <forwarded Nginx URL> --tokens <seed tokens> \
#     --app-exec "kubectl exec -n <namespace> <app pod> --"
# Optionally add --k6 docker, or run k6 in-cluster in a later version.
echo "MODE=k8s is not implemented yet; use MODE=direct or MODE=docker." >&2
exit 2
