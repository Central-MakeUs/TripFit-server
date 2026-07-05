package com.tripfit.tripfit.auth.service.security;

public interface TokenRevocationChecker {

	boolean isRevoked(String jti);
}
