package com.tripfit.tripfit.auth.client;

import org.springframework.stereotype.Component;

@Component
public class NoOpTokenRevocationChecker implements TokenRevocationChecker {

  @Override
  public boolean isRevoked(String jti) {
    // TODO: wave 4 RTR+Redis 도입 시 jti 블랙리스트 조회로 교체
    return false;
  }
}
