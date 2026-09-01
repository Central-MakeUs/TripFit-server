package com.tripfit.tripfit.auth.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class AppleOAuthClient {

  private static final Logger log = LoggerFactory.getLogger(AppleOAuthClient.class);

  private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";

  private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

  private static final String AUDIENCE = "https://appleid.apple.com";

  private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

  private final RestClient restClient;

  private final OAuthProperties oAuthProperties;

  public AppleOAuthClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

  // 애플 인가 코드를 이용해 Refresh Token을 발급받는 서버 간 통신입니다.
  // 애플은 요청 시 발급된 Client Secret(서명된 JWT)을 요구합니다.
  // API 스펙 참조:
  // https://developer.apple.com/documentation/sign_in_with_apple/generate_and_validate_tokens
  public String exchangeAuthorizationCodeForRefreshToken(
      String authorizationCode,
      String clientId) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", clientId);
    form.add("client_secret", buildClientSecretJwt(clientId));
    form.add("code", authorizationCode);
    form.add("grant_type", "authorization_code");
    JsonNode response =
        restClient
            .post()
            .uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                (request, clientResponse) -> {
                  SocialIntegrationLog.warn(
                      log,
                      SocialLogContext.of(
                          SocialProvider.APPLE,
                          SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
                          .withHttpStatus(clientResponse.getStatusCode().value()),
                      "Apple login authorization code exchange failed");
                  throw new IllegalStateException(
                      "Apple token endpoint error: " + clientResponse.getStatusCode());
                })
            .body(JsonNode.class);
    if (response == null || !response.has("refresh_token")) {
      throw new IllegalStateException("Apple token response missing refresh_token");
    }
    return response.get("refresh_token").asText();
  }

  // 유저 탈퇴 시 연동 해제를 위해 애플 서버에 Refresh Token 강제 폐기를 요청합니다.
  public void revokeRefreshToken(String refreshToken, String clientId) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", clientId);
    form.add("client_secret", buildClientSecretJwt(clientId));
    form.add("token", refreshToken);
    form.add("token_type_hint", "refresh_token");
    try {
      restClient
          .post()
          .uri(REVOKE_URL)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception exception) {
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.APPLE, SocialIntegrationAction.LOGIN_TOKEN_REVOKE),
          "Apple refresh token revoke failed",
          exception);
    }
  }

  // 애플 REST API 호출 시 인증을 위해 필요한 Client Secret (JWT 형식)을 생성합니다.
  // 내부적으로 애플 개발자 키(P8)를 사용하여 ES256 알고리즘으로 서명합니다.
  private String buildClientSecretJwt(String clientId) {
    try {
      Instant now = Instant.now();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(oAuthProperties.getAppleTeamId())
              .subject(clientId)
              .audience(AUDIENCE)
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(CLIENT_SECRET_TTL)))
              .build();
      JWSHeader header =
          new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(oAuthProperties.getAppleKeyId()).build();
      SignedJWT signedJwt = new SignedJWT(header, claims);
      signedJwt.sign(new ECDSASigner(parsePrivateKey(oAuthProperties.getApplePrivateKey())));
      return signedJwt.serialize();
    } catch (JOSEException | GeneralSecurityException exception) {
      throw new IllegalStateException("Failed to build Apple client_secret JWT", exception);
    }
  }

  private ECPrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
    String base64 =
        pem.replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] decoded = Base64.getDecoder().decode(base64);
    KeyFactory keyFactory = KeyFactory.getInstance("EC");
    return (ECPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
  }
}
