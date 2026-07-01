resource "serverspace_isolated_network" "reactive_net" {
  location       = var.location
  name           = "reactive_net"
  description    = "Example for Terraform"
  network_prefix = "192.168.0.0"
  mask           = 24
}

resource "serverspace_server" "control_plane" {
  image            = var.image_family
  name             = var.control_plane_name
  location         = var.location
  cpu              = var.control_plane_cpu
  ram              = var.control_plane_ram
  boot_volume_size = var.control_plane_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 50
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }

  ssh_keys = [
    serverspace_ssh.terraform.id,
  ]
}

resource "serverspace_server" "apps" {
  image            = var.image_family
  name             = var.apps_name
  location         = var.location
  cpu              = var.apps_cpu
  ram              = var.apps_ram
  boot_volume_size = var.apps_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 50
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }

  ssh_keys = [
    serverspace_ssh.terraform.id,
  ]
}

resource "serverspace_server" "postgres" {
  image            = var.image_family
  name             = var.postgres_name
  location         = var.location
  cpu              = var.postgres_cpu
  ram              = var.postgres_ram
  boot_volume_size = var.postgres_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 70
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }
}

resource "serverspace_server" "gitlab" {
  image            = var.image_family
  name             = var.gitlab_name
  location         = var.location
  cpu              = var.gitlab_cpu
  ram              = var.gitlab_ram
  boot_volume_size = var.gitlab_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 50
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }
}
