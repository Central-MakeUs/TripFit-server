package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleOAuthClientTest {

  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

  private GoogleOAuthClient newClient(MockRestServiceServer[] serverHolder) {
    RestClient.Builder builder = RestClient.builder();
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    OAuthProperties properties = new OAuthProperties();
    properties.setGoogleClientId("web-client-id");
    properties.setGoogleClientSecret("web-client-secret");
    return new GoogleOAuthClient(builder.build(), properties);
  }

  @Test
  void exchangeAuthorizationCodeForRefreshToken_whenRefreshTokenPresent_returnsIt() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andRespond(
            withSuccess(
                """
                    {"access_token": "at", "refresh_token": "rt-value", "token_type": "Bearer", "expires_in": 3600}
                    """,
                MediaType.APPLICATION_JSON));

    String refreshToken = client.exchangeAuthorizationCodeForRefreshToken("auth-code-123");

    assertThat(refreshToken).isEqualTo("rt-value");
    serverHolder[0].verify();
  }

  // 재로그인 등 최초 동의가 아닌 케이스 — Google이 refresh_token을 안 내려줘도 예외가 아니라 null이어야 함
  @Test
  void exchangeAuthorizationCodeForRefreshToken_whenRefreshTokenAbsent_returnsNull() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andRespond(
            withSuccess(
                """
                    {"access_token": "at", "token_type": "Bearer", "expires_in": 3600}
                    """,
                MediaType.APPLICATION_JSON));

    String refreshToken = client.exchangeAuthorizationCodeForRefreshToken("auth-code-123");

    assertThat(refreshToken).isNull();
  }

  @Test
  void exchangeAuthorizationCodeForRefreshToken_errorStatus_throws() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .body("""
                    {"error":"invalid_grant"}
                    """)
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.exchangeAuthorizationCodeForRefreshToken("bad-code"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid_grant");
  }

  @Test
  void revokeRefreshToken_success_sendsRevokeRequest() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(REVOKE_URL + "?token=rt-value"))
        .andRespond(withSuccess());

    client.revokeRefreshToken("rt-value");

    serverHolder[0].verify();
  }

  @Test
  void revokeRefreshToken_providerFailure_doesNotThrow() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(REVOKE_URL + "?token=rt-value"))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });

    assertThatCode(() -> client.revokeRefreshToken("rt-value")).doesNotThrowAnyException();
  }
}
