package com.tripfit.tripfit.auth.service.social;

import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SocialTokenVerifierRegistry {

	private final Map<SocialProvider, SocialTokenVerifier> verifiers;

	public SocialTokenVerifierRegistry(List<SocialTokenVerifier> verifierList) {
		this.verifiers = new EnumMap<>(SocialProvider.class);
		for (SocialTokenVerifier verifier : verifierList) {
			this.verifiers.put(verifier.getProvider(), verifier);
		}
	}

	public SocialTokenVerifier getVerifier(SocialProvider provider) {
		SocialTokenVerifier verifier = verifiers.get(provider);
		if (verifier == null) {
			throw new TripFitException(ErrorCode.AUTH_INVALID_REQUEST);
		}
		return verifier;
	}
}
