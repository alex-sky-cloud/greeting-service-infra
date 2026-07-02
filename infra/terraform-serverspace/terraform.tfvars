api_key = "06c3615451ba5fcd7f3cc7834f3dce14c3abe2b567ef67670085d156485db141"

ssh_public_key = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCYyjiZMKG8/4GZBC6pV/+bqjHHvwDRsNwYhB/NIeBmvAMY4F2ya4U8IiU7azzDlcSRWOrIZ38ZE6D5wB/J8h6sxn+rNqpPy7d3XhiwJhLLrt20wI84+yj29iu6PeC80mG6GrJvCjo6lhm99vbpUn+t7jby1a6JMU1UcP2eBC32bEfOCSy8J615cA7c4KY3zKK64/zNNBTnP08Xy3/lgf9d2bR1u38PjIrQ+tlzJinhipYRXYy4YPOOWeucZ5zm9t3EUBAaUbEpiW0xp8T5wLajH0qQYqRL7onWylINPpH10T5zLcCxcRMOH4zM3wopzASWYoEQ2Gr78zL+9I0h3k/VbK2i6/4KeMWC5eSWZ9tLpl6GN7eCdIqfQAg1KGDeUqE2jgWKB8MeHJ18scqFj2cNp3PkmOxTdN8xFsszmQy4KMesajGNbzQJV4ND2t4QFx9unXv1mhLjCsEmVXCPd3slkQ/9ifgyiZX2SLX5f6pCEjNAVZbWgk0/3Epkd6EMpTc= it@LAPTOP-2BOG5VNI"

location     = "ds1"
image_family = "Ubuntu-20.04-X64"

control_plane_name = "k8s-control-plane-1"
control_plane_cpu  = 2
control_plane_ram  = 4096 # 8192
control_plane_disk = 40

apps_name = "k8s-apps-1"
apps_cpu  = 2
apps_ram  = 8192
apps_disk = 40

postgres_name = "postgres-1"
postgres_cpu  = 2
postgres_ram  = 4096 # 8192
postgres_disk = 40

gitlab_name = "gitlab-1"
gitlab_cpu  = 4
gitlab_ram  = 8192
gitlab_disk = 40