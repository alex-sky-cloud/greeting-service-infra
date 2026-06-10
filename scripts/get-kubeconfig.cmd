@echo off
wsl -d Ubuntu bash -lc "bash /mnt/d/\!_*/greeting-service-infra/scripts/get-kubeconfig.sh"
