package com.tripfit.tripfit.auth.jwt;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

// JWT 인증 토큰 — principal=UUID, 역할 권한 없음(NO_AUTHORITIES). 방 권한은 Trip*Only 인터셉터가 담당.
// jti·expiresAt은 로그아웃이 아니라 인증된 요청 도중(예: 탈퇴)에 현재 access token 자체를 블랙리스트에
// 올려야 하는 호출부(UserController.withdraw 등)를 위해 들고 있음
public class JwtAuthentication extends AbstractAuthenticationToken {

  private final UUID userId;

  private final String jti;

  private final Instant expiresAt;

  public JwtAuthentication(UUID userId, String jti, Instant expiresAt) {
    super(AuthorityUtils.NO_AUTHORITIES);
    this.userId = userId;
    this.jti = jti;
    this.expiresAt = expiresAt;
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

  public String getJti() {
    return jti;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
