package com.tripfit.tripfit.auth.client;

import com.tripfit.tripfit.auth.config.OAuthProperties;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
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
	// 이 검증기가 담당하는 소셜 제공자를 애플로 반환함
	public SocialProvider getProvider() {
		return SocialProvider.APPLE;
	}

	@Override
	// 애플 ID 토큰의 서명과 audience를 검증해 사용자 프로필을 추출함
	public OAuthProfile verify(String token) {
		// 1. 애플 서비스 ID가 설정돼 있는지 확인함
		String appleClientId = oAuthProperties.getAppleClientId();
		if (appleClientId == null || appleClientId.isBlank()) {
			throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN, "Apple client ID is not configured");
		}
		try {
			// 2. 토큰 서명을 검증하고 클레임을 파싱함
			JWTClaimsSet claims = processToken(token);
			if (!hasValidAudience(claims, appleClientId)) {
				throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
			}
			String subject = claims.getSubject();
			if (subject == null || subject.isBlank()) {
				throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
			}
			return new OAuthProfile(SocialProvider.APPLE, subject, claims.getStringClaim("email"));
		} catch (TripFitException exception) {
			// 비즈니스 검증에서 만든 인증 예외는 그대로 상위로 전달함
			throw exception;
		} catch (Exception exception) {
			// 외부 공개키 조회나 JWT 처리 실패 시 유효하지 않은 토큰으로 처리함
			throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
		}
	}

	// 애플 공개키를 사용해 ID 토큰 서명을 검증하고 클레임을 반환함
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

	// 토큰 audience에 현재 서비스용 애플 클라이언트 ID가 포함되는지 확인함
	private boolean hasValidAudience(JWTClaimsSet claims, String appleClientId) throws ParseException {
		List<String> audiences = claims.getAudience();
		return audiences != null && audiences.contains(appleClientId);
	}
}
