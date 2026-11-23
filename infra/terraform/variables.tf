variable "account_id" {
  description = "Verified AWS account ID for the target environment."
  type        = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "Provide a 12-digit AWS account ID."
  }
}

variable "environment" {
  description = "Stack name suffix within the target AWS account."
  type        = string
  default     = "prod"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,15}$", var.environment))
    error_message = "Use 1–16 lowercase letters, digits or hyphens, starting with a letter."
  }
}

variable "availability_zone" {
  description = "A Seoul availability zone verified with Lightsail GetRegions."
  type        = string
  validation {
    condition     = can(regex("^ap-northeast-2[a-z]$", var.availability_zone))
    error_message = "Select a Seoul Lightsail availability zone."
  }
}

variable "admin_ipv4_cidr" {
  description = "Administrator's actual public IPv4 address as a single /32."
  type        = string
  validation {
    condition     = can(cidrnetmask(var.admin_ipv4_cidr)) && can(regex("^[0-9.]+/32$", var.admin_ipv4_cidr)) && var.admin_ipv4_cidr != "0.0.0.0/32"
    error_message = "Provide one administrator IPv4 address with /32."
  }
}

variable "admin_public_key" {
  description = "RSA OpenSSH public key supported by Lightsail ImportKeyPair. Never provide the private key."
  type        = string
  validation {
    condition     = can(regex("^ssh-rsa [A-Za-z0-9+/]+=*( [^\r\n]+)?$", trimspace(var.admin_public_key)))
    error_message = "Provide a single OpenSSH RSA public key (ssh-rsa)."
  }
}
