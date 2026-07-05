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
public class GoogleTokenVerifier implements SocialTokenVerifier {

	private static final URL GOOGLE_JWK_URL;

	static {
		try {
			GOOGLE_JWK_URL = new URL("https://www.googleapis.com/oauth2/v3/certs");
		} catch (MalformedURLException exception) {
			throw new IllegalStateException("Invalid Google JWK URL", exception);
		}
	}

	private final OAuthProperties oAuthProperties;

	public GoogleTokenVerifier(OAuthProperties oAuthProperties) {
		this.oAuthProperties = oAuthProperties;
	}

	@Override
	public SocialProvider getProvider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	public OAuthProfile verify(String token) {
		List<String> allowedAudiences = oAuthProperties.getGoogleClientIds();
		if (allowedAudiences.isEmpty()) {
			throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN, "Google client ID is not configured");
		}
		try {
			JWTClaimsSet claims = processToken(token);
			if (!hasValidAudience(claims, allowedAudiences)) {
				throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
			}
			String subject = claims.getSubject();
			if (subject == null || subject.isBlank()) {
				throw new TripFitException(ErrorCode.AUTH_INVALID_TOKEN);
			}
			return new OAuthProfile(
					SocialProvider.GOOGLE,
					subject,
					claims.getStringClaim("email"),
					claims.getStringClaim("name"),
					claims.getStringClaim("picture")
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
		JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(GOOGLE_JWK_URL);
		JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
		processor.setJWSKeySelector(keySelector);
		return processor.process(token, null);
	}

	private boolean hasValidAudience(JWTClaimsSet claims, List<String> allowedAudiences) throws ParseException {
		List<String> audiences = claims.getAudience();
		if (audiences == null || audiences.isEmpty()) {
			return false;
		}
		return audiences.stream().anyMatch(allowedAudiences::contains);
	}
}
