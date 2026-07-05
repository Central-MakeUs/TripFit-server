package com.tripfit.tripfit.auth.service.social;

import com.tripfit.tripfit.auth.config.OAuthProperties;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.common.exception.ErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

@Component
public class AppleTokenVerifier implements SocialTokenVerifier {

	private static final URL APPLE_JWK_URL;

	static {
		try {
			APPLE_JWK_URL = new URL("https://appleid.apple.com/auth/keys");
		} catch (MalformedURLException exception) {
			throw new IllegalStateException("Invalid Apple JWK URL", exception);
		}
	}

	private final OAuthProperties oAuthProperties;

	public AppleTokenVerifier(OAuthProperties oAuthProperties) {
		this.oAuthProperties = oAuthProperties;
	}

	@Override
	public SocialProvider getProvider() {
		return SocialProvider.APPLE;
	}

	@Override
	public OAuthProfile verify(String token) {
		String appleClientId = oAuthProperties.getAppleClientId();
		if (appleClientId == null || appleClientId.isBlank()) {
			throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN, "Apple client ID is not configured");
		}
		try {
			JWTClaimsSet claims = processToken(token);
			if (!hasValidAudience(claims, appleClientId)) {
				throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
			}
			String subject = claims.getSubject();
			if (subject == null || subject.isBlank()) {
				throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
			}
			return new OAuthProfile(
					SocialProvider.APPLE,
					subject,
					claims.getStringClaim("email"),
					null,
					null
			);
		} catch (TripFitException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
		}
	}

	private JWTClaimsSet processToken(String token)
			throws ParseException, JOSEException, BadJOSEException, java.net.MalformedURLException {
		ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
		JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(APPLE_JWK_URL);
		JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
				JWSAlgorithm.RS256,
				keySource
		);
		processor.setJWSKeySelector(keySelector);
		return processor.process(token, null);
	}

	private boolean hasValidAudience(JWTClaimsSet claims, String appleClientId) throws ParseException {
		List<String> audiences = claims.getAudience();
		return audiences != null && audiences.contains(appleClientId);
	}
}
