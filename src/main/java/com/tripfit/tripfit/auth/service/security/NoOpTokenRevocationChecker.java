package com.tripfit.tripfit.auth.service.security;

import org.springframework.stereotype.Component;

@Component
public class NoOpTokenRevocationChecker implements TokenRevocationChecker {

	@Override
	public boolean isRevoked(String jti) {
		return false;
	}
}
