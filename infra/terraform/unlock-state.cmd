@echo off
wsl -d Ubuntu bash -lc "pkill terraform 2>/dev/null; cd /mnt/d/\!_*/greeting-service-infra/infra/terraform && rm -f .terraform.tfstate.lock.info && terraform force-unlock -force 2d5b81fa-6067-5ed7-5307-2ab3a76fa512"
