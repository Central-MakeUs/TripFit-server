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
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtProperties jwtProperties;

  private final byte[] secretBytes;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.secretBytes = jwtProperties.getSecret().getBytes();
    // TODO: secret 최소 32바이트 검증을 부팅 시 강제 — 현재는 env 누락 시 런타임 서명 실패에 의존
  }

  // 사용자 ID를 기반으로 서명된 JWT 액세스 토큰을 생성함
  public String createAccessToken(UUID userId) {
    try {
      // 1. 발급 시각과 만료 시각을 계산해 JWT 클레임을 구성함
      Instant now = Instant.now();
      Instant expiry = now.plusSeconds(jwtProperties.getAccessExpirationSeconds());
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(userId.toString())
              .jwtID(UUID.randomUUID().toString())
              .issueTime(Date.from(now))
              .expirationTime(Date.from(expiry))
              .build();

      // 2. HS256 서명으로 토큰을 생성하고 직렬화함
      SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      signedJwt.sign(new MACSigner(secretBytes));
      return signedJwt.serialize();
    } catch (JOSEException exception) {
      // 서명 생성 중 암호화 처리 오류가 발생하면 서버 내부 오류로 간주함
      throw new IllegalStateException("Failed to create access token", exception);
    }
  }

  // 액세스 토큰을 검증하고 사용자 ID와 jti 클레임을 추출함
  public AccessTokenClaims parseAccessToken(String accessToken) {
    try {
      // 1. 토큰 문자열을 파싱하고 서명이 유효한지 확인함
      SignedJWT signedJwt = SignedJWT.parse(accessToken);
      if (!signedJwt.verify(new MACVerifier(secretBytes))) {
        throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
      }

      // 2. 만료 시각을 검증해 만료된 토큰을 차단함
      JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
      Date expiration = claims.getExpirationTime();
      if (expiration == null || expiration.before(new Date())) {
        throw new TripFitException(AuthErrorCode.AUTH_EXPIRED);
      }

      // 3. subject·jti 값을 검증하고 클레임 record로 반환함
      String jti = claims.getJWTID();
      if (jti == null || jti.isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
      }
      String subject = claims.getSubject();
      if (subject == null || subject.isBlank()) {
        throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
      }
      return new AccessTokenClaims(UUID.fromString(subject), jti);
    } catch (ParseException | JOSEException exception) {
      // 파싱·서명 실패는 클라이언트 토큰 오류와 구분하지 않고 AUTH_INVALID_TOKEN으로 통일
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    } catch (IllegalArgumentException exception) {
      // subject가 UUID가 아니면 위조·손상 토큰으로 간주
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
  }

  public UUID parseUserId(String accessToken) {
    return parseAccessToken(accessToken).userId();
  }

  public long getAccessExpirationSeconds() {
    return jwtProperties.getAccessExpirationSeconds();
  }
}
