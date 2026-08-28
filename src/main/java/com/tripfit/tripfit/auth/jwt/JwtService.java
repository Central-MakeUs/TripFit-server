package com.tripfit.tripfit.auth.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtProperties jwtProperties;

  private static final int MIN_SECRET_BYTES = 32;

  private final byte[] secretBytes;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.secretBytes = jwtProperties.getSecret().getBytes();
    if (secretBytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "tripfit.jwt.secret must be at least "
              + MIN_SECRET_BYTES
              + " bytes for HS256 (was "
              + secretBytes.length
              + ")");
    }
  }

  public String createAccessToken(UUID userId) {
    try {

      Instant now = Instant.now();
      Instant expiry = now.plusSeconds(jwtProperties.getAccessExpirationSeconds());
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(userId.toString())
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

  public AccessTokenClaims parseAccessToken(String accessToken) {
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

      String subject = claims.getSubject();
      if (subject == null || subject.isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
      }
      return new AccessTokenClaims(UUID.fromString(subject));
    } catch (ParseException | JOSEException exception) {

      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    } catch (IllegalArgumentException exception) {

      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  public long getAccessExpirationSeconds() {
    return jwtProperties.getAccessExpirationSeconds();
  }
}
