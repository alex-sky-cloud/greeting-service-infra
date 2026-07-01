resource "serverspace_ssh" "terraform" {
  name       = "terraform-key"
  public_key = var.ssh_public_key
}