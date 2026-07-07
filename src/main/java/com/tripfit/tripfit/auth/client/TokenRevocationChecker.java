package com.tripfit.tripfit.auth.client;

public interface TokenRevocationChecker {

	boolean isRevoked(String jti);
}
