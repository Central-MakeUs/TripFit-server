locals {
  name = "tripfit-${var.environment}"
}

resource "aws_lightsail_key_pair" "admin" {
  name       = "${local.name}-admin"
  public_key = trimspace(var.admin_public_key)
}

resource "aws_lightsail_instance" "server" {
  name              = local.name
  availability_zone = var.availability_zone
  blueprint_id      = "ubuntu_24_04"
  bundle_id         = "small_3_0"
  ip_address_type   = "ipv4"
  key_pair_name     = aws_lightsail_key_pair.admin.name
  user_data         = file("${path.module}/docker-init.sh")

  lifecycle {
    prevent_destroy = true
    # Lightsail accepts user_data only at creation. Update existing hosts over SSH.
    ignore_changes = [user_data]
  }
}

resource "aws_lightsail_static_ip" "server" {
  name = "${local.name}-ipv4"
}

resource "aws_lightsail_static_ip_attachment" "server" {
  static_ip_name = aws_lightsail_static_ip.server.name
  instance_name  = aws_lightsail_instance.server.name
}

resource "aws_lightsail_instance_public_ports" "server" {
  instance_name = aws_lightsail_instance.server.name

  port_info {
    protocol  = "tcp"
    from_port = 22
    to_port   = 22
    cidrs     = [var.admin_ipv4_cidr]
  }
  port_info {
    protocol  = "tcp"
    from_port = 80
    to_port   = 80
    cidrs     = ["0.0.0.0/0"]
  }
  port_info {
    protocol  = "tcp"
    from_port = 443
    to_port   = 443
    cidrs     = ["0.0.0.0/0"]
  }
}

output "public_ipv4" {
  value = aws_lightsail_static_ip.server.ip_address
}

output "ssh_user" {
  value = "ubuntu"
}
