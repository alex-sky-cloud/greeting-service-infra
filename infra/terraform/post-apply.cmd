@echo off
wsl -d Ubuntu bash -lc "cd /mnt/d/\!_*/greeting-service-infra/infra/terraform && bash post-apply.sh"
