
source ./infra-servers.env
ssh -i ~/.ssh/id_ed25519 root@${DEVTOOLS_IP} "echo connected"
ssh -i ~/.ssh/id_ed25519 root@${K8S_MASTER_IP} "echo connected"
ssh -i ~/.ssh/id_ed25519 root@${TRAEFIK_1_IP} "echo connected"

