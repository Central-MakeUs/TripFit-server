package com.tripfit.tripfit.auth.jwt;

import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

// JWT 인증 토큰 — principal=UUID, 역할 권한 없음(NO_AUTHORITIES). 방 권한은 Trip*Only 인터셉터가 담당
public class JwtAuthentication extends AbstractAuthenticationToken {

  private final UUID userId;

  public JwtAuthentication(UUID userId) {
    super(AuthorityUtils.NO_AUTHORITIES);
    this.userId = userId;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return userId;
  }
}
