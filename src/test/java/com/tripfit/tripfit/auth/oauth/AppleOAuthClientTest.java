package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleOAuthClientTest {

  private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";

  private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

  private static final String TEST_PRIVATE_KEY_PEM =
      """
          -----BEGIN PRIVATE KEY-----
          MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgCb/a998U/TaTA0ff
          MW6LzP6cmUWiywvwsaDsuH/tNjGhRANCAASCjdXpt6si+yXZWUNoAIeQ+2rmX6LX
          2Rnn9DtJiz01rHJ6PicABO77MvFPLmPdDcdVx6sAJNqpK7+iunvxlrZZ
          -----END PRIVATE KEY-----
          """;

  private static final String TEST_PUBLIC_KEY_PEM =
      """
          -----BEGIN PUBLIC KEY-----
          MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEgo3V6berIvsl2VlDaACHkPtq5l+i
          19kZ5/Q7SYs9Naxyej4nAATu+zLxTy5j3Q3HVcerACTaqSu/orp78Za2WQ==
          -----END PUBLIC KEY-----
          """;

  private AppleOAuthClient newClient(MockRestServiceServer[] serverHolder) {
    RestClient.Builder builder = RestClient.builder();
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    OAuthProperties properties = new OAuthProperties();
    properties.setAppleBundleId("com.tripfit.app");
    properties.setAppleServiceId("com.tripfit.service");
    properties.setAppleTeamId("TEAM123456");
    properties.setAppleKeyId("KEY123456");
    properties.setApplePrivateKey(TEST_PRIVATE_KEY_PEM);
    return new AppleOAuthClient(builder.build(), properties);
  }

  @Test
  void exchangeAuthorizationCodeForRefreshToken_success_returnsRefreshTokenWithValidClientSecretJwt()
      throws Exception {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    AppleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andExpect(request -> assertValidClientSecretJwt(request, "com.tripfit.app"))
        .andRespond(
            withSuccess(
                """
                    {"access_token": "at", "refresh_token": "rt-value", "token_type": "Bearer", "expires_in": 3600}
                    """,
                MediaType.APPLICATION_JSON));

    String refreshToken =
        client.exchangeAuthorizationCodeForRefreshToken("auth-code-123", "com.tripfit.app");

    assertThat(refreshToken).isEqualTo("rt-value");
    serverHolder[0].verify();
  }

  @Test
  void exchangeAuthorizationCodeForRefreshToken_withServiceId_usesServiceIdAsClientId()
      throws Exception {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    AppleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andExpect(request -> assertValidClientSecretJwt(request, "com.tripfit.service"))
        .andRespond(
            withSuccess(
                """
                    {"access_token": "at", "refresh_token": "rt-value", "token_type": "Bearer", "expires_in": 3600}
                    """,
                MediaType.APPLICATION_JSON));

    String refreshToken =
        client.exchangeAuthorizationCodeForRefreshToken("auth-code-123", "com.tripfit.service");

    assertThat(refreshToken).isEqualTo("rt-value");
    serverHolder[0].verify();
  }

  @Test
  void exchangeAuthorizationCodeForRefreshToken_errorStatus_throws() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    AppleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("""
                    {"error":"invalid_grant"}
                    """)
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(
        () -> client.exchangeAuthorizationCodeForRefreshToken("bad-code", "com.tripfit.app"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void revokeRefreshToken_success_sendsRevokeRequest() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    AppleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(REVOKE_URL))
        .andRespond(withSuccess());

    client.revokeRefreshToken("rt-value", "com.tripfit.app");

    serverHolder[0].verify();
  }

  @Test
  void revokeRefreshToken_providerFailure_doesNotThrow() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    AppleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(REVOKE_URL))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });

    assertThatCode(() -> client.revokeRefreshToken("rt-value", "com.tripfit.app"))
        .doesNotThrowAnyException();
  }

  private void assertValidClientSecretJwt(
      org.springframework.http.client.ClientHttpRequest request,
      String expectedClientId) {
    try {
      String body = ((MockClientHttpRequest) request).getBodyAsString();
      Map<String, String> form = parseForm(body);
      SignedJWT jwt = SignedJWT.parse(form.get("client_secret"));

      assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
      assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY123456");
      assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123456");
      assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(expectedClientId);
      assertThat(jwt.getJWTClaimsSet().getAudience()).contains("https://appleid.apple.com");
      assertThat(jwt.verify(new ECDSAVerifier(parsePublicKey(TEST_PUBLIC_KEY_PEM)))).isTrue();
    } catch (Exception exception) {
      throw new AssertionError("client_secret JWT 검증 실패", exception);
    }
  }

  private Map<String, String> parseForm(String body) {
    return Arrays.stream(body.split("&"))
        .map(pair -> pair.split("=", 2))
        .collect(
            Collectors.toMap(
                kv -> URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));
  }

  private ECPublicKey parsePublicKey(String pem) throws Exception {
    String base64 =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    byte[] decoded = Base64.getDecoder().decode(base64);
    KeyFactory keyFactory = KeyFactory.getInstance("EC");
    return (ECPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
  }
}
