package com.tripfit.tripfit.auth.client;

import org.springframework.stereotype.Component;

@Component
public class NoOpTokenRevocationChecker implements TokenRevocationChecker {

	@Override
	// 토큰 폐기 저장소를 아직 사용하지 않아 항상 폐기되지 않은 것으로 간주함
	public boolean isRevoked(String jti) {
		return false;
	}
}
