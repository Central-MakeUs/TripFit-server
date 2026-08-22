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

// Apple Sign In authorizationCode → refresh token 교환·revoke — client_secret은 .p8로 서명한 ES256
// JWT(호출마다 새로 생성, 캐싱 없음)
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

  // authorization code → refresh token 교환 — 실패는 예외로 던짐(호출부가 best-effort로 감쌈). clientId는
  // authorizationCode를 발급한 것과 반드시 동일해야 함(Bundle ID로 받은 code를 Services ID로 교환하면 Apple이 거부)
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

  // refresh token revoke (best-effort — 실패해도 탈퇴 트랜잭션을 막지 않음). clientId는 로그인 시점에 저장해둔
  // AppleCredential.appleClientId를 그대로 넘겨받아야 함(교환 때와 동일한 값)
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

  // Team ID·Key ID·.p8 개인키로 ES256 서명한 client_secret JWT를 매 호출마다 새로 발급함(캐싱 없음 — 호출 빈도가 낮아 불필요).
  // sub는 호출부가 넘긴 clientId(교환·revoke 각각의 client_id와 항상 동일해야 함)
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

  // .p8 PEM(PKCS#8) 텍스트 → EC 개인키 — env 전달 과정의 이스케이프된 개행("\n")도 함께 정규화
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
