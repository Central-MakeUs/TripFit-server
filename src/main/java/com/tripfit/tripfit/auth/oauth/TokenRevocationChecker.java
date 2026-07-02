package com.tripfit.tripfit.auth.oauth;

public interface TokenRevocationChecker {

  boolean isRevoked(String jti);
}
