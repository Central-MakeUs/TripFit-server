package com.tripfit.tripfit.auth.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class JwtAuthentication extends AbstractAuthenticationToken {

	private final Long userId;

	public JwtAuthentication(Long userId) {
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
