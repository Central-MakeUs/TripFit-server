mock_provider "aws" {}

variables {
  account_id        = "123456789012"
  availability_zone = "ap-northeast-2a"
  admin_ipv4_cidr   = "203.0.113.4/32"
  admin_public_key  = "ssh-rsa AAAA fixture"
}

run "one_ipv4_server_with_restricted_ssh" {
  command = plan

  assert {
    condition     = aws_lightsail_instance.server.bundle_id == "small_3_0" && aws_lightsail_instance.server.ip_address_type == "ipv4"
    error_message = "Only the approved 2GB bundle with IPv4 is expected."
  }
  assert {
    condition = toset([for p in aws_lightsail_instance_public_ports.server.port_info : "${p.protocol}/${p.from_port}/${p.to_port}"]) == toset(["tcp/22/22", "tcp/80/80", "tcp/443/443"]) && alltrue([
      for p in aws_lightsail_instance_public_ports.server.port_info : p.from_port == 22 ? toset(p.cidrs) == toset([var.admin_ipv4_cidr]) : toset(p.cidrs) == toset(["0.0.0.0/0"])
    ])
    error_message = "Only admin SSH and the public web ports are permitted."
  }
}

run "rejects_world_open_ssh" {
  command = plan

  variables {
    admin_ipv4_cidr = "0.0.0.0/0"
  }

  expect_failures = [var.admin_ipv4_cidr]
}

run "rejects_key_not_supported_by_lightsail" {
  command = plan

  variables {
    admin_public_key = "ssh-ed25519 AAAA fixture"
  }

  expect_failures = [var.admin_public_key]
}
