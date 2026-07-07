package com.tripfit.tripfit.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tripfit.tripfit.auth.config.JwtProperties;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

	private final JwtProperties jwtProperties;
	private final byte[] secretBytes;

	public JwtService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
		this.secretBytes = jwtProperties.getSecret().getBytes();
		if (secretBytes.length < 32) {
			throw new IllegalStateException("JWT secret must be at least 32 bytes");
		}
	}

	public String createAccessToken(Long userId) {
		try {
			Instant now = Instant.now();
			Instant expiry = now.plusSeconds(jwtProperties.getAccessExpirationSeconds());
			JWTClaimsSet claims = new JWTClaimsSet.Builder()
					.subject(String.valueOf(userId))
					.jwtID(UUID.randomUUID().toString())
					.issueTime(Date.from(now))
					.expirationTime(Date.from(expiry))
					.build();
			SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
			signedJwt.sign(new MACSigner(secretBytes));
			return signedJwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException("Failed to create access token", exception);
		}
	}

	public Long parseUserId(String accessToken) {
		try {
			SignedJWT signedJwt = SignedJWT.parse(accessToken);
			if (!signedJwt.verify(new MACVerifier(secretBytes))) {
				throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
			}
			JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
			Date expiration = claims.getExpirationTime();
			if (expiration == null || expiration.before(new Date())) {
				throw new TripFitException(AuthErrorCode.AUTH_EXPIRED);
			}
			return Long.parseLong(claims.getSubject());
		} catch (ParseException | JOSEException exception) {
			throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
		} catch (NumberFormatException exception) {
			throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
		}
	}

	public long getAccessExpirationSeconds() {
		return jwtProperties.getAccessExpirationSeconds();
	}
}
