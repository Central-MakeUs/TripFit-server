package com.tripfit.tripfit.user.googlecalendar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import com.tripfit.tripfit.user.googlecalendar.exception.GoogleCalendarAuthException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GoogleCalendarOAuthClientTest {

  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private static final String FREE_BUSY_URL = "https://www.googleapis.com/calendar/v3/freeBusy";

  private GoogleCalendarOAuthClient newClient(MockRestServiceServer[] serverHolder) {
    RestClient.Builder builder = RestClient.builder();
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    OAuthProperties properties = new OAuthProperties();
    properties.setGoogleCalendarClientId("calendar-client-id");
    properties.setGoogleCalendarClientSecret("calendar-client-secret");
    return new GoogleCalendarOAuthClient(builder.build(), properties);
  }

  @Test
  void exchangeAuthorizationCode_whenRedirectUriNull_sendsEmptyRedirectUri() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleCalendarOAuthClient client = newClient(serverHolder);
    MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
    expectedForm.add("code", "auth-code-123");
    expectedForm.add("client_id", "calendar-client-id");
    expectedForm.add("client_secret", "calendar-client-secret");
    expectedForm.add("redirect_uri", "");
    expectedForm.add("grant_type", "authorization_code");
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andExpect(content().formData(expectedForm))
        .andRespond(
            withSuccess(
                """
                    {"access_token": "at", "refresh_token": "rt-value", "token_type": "Bearer", "expires_in": 3600}
                    """,
                MediaType.APPLICATION_JSON));

    GoogleOAuthTokenResponse response = client.exchangeAuthorizationCode("auth-code-123", null);

    assertThat(response.refreshToken()).isEqualTo("rt-value");
    serverHolder[0].verify();
  }

  @Test
  void exchangeAuthorizationCode_whenRedirectUriPresent_forwardsActualValue() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleCalendarOAuthClient client = newClient(serverHolder);
    MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
    expectedForm.add("code", "auth-code-123");
    expectedForm.add("client_id", "calendar-client-id");
    expectedForm.add("client_secret", "calendar-client-secret");
    expectedForm.add("redirect_uri", "https://tripfit.online/settings/google-calendar/callback");
    expectedForm.add("grant_type", "authorization_code");
    serverHolder[0]
        .expect(requestTo(TOKEN_URL))
        .andExpect(content().formData(expectedForm))
        .andRespond(
            withSuccess(
                """
                    {"access_token": "at", "refresh_token": "rt-value", "token_type": "Bearer", "expires_in": 3600}
                    """,
                MediaType.APPLICATION_JSON));

    GoogleOAuthTokenResponse response =
        client.exchangeAuthorizationCode(
            "auth-code-123",
            "https://tripfit.online/settings/google-calendar/callback");

    assertThat(response.refreshToken()).isEqualTo("rt-value");
    serverHolder[0].verify();
  }

  @Test
  void queryFreeBusy_when403InsufficientScope_throwsGoogleCalendarAuthException() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleCalendarOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(FREE_BUSY_URL))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .body(
                    """
                        {"error": {"code": 403, "message": "Request had insufficient authentication scopes.",
                          "errors": [{"message": "Insufficient Permission", "domain": "global", "reason": "insufficientPermissions"}],
                          "status": "PERMISSION_DENIED",
                          "details": [{"@type": "type.googleapis.com/google.rpc.ErrorInfo",
                            "reason": "ACCESS_TOKEN_SCOPE_INSUFFICIENT", "domain": "googleapis.com"}]}}
                        """)
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(
        () -> client.queryFreeBusy(
            UUID.randomUUID(),
            "at",
            Instant.parse("2026-08-20T00:00:00Z"),
            Instant.parse("2026-08-25T00:00:00Z")))
        .isInstanceOf(GoogleCalendarAuthException.class);
    serverHolder[0].verify();
  }

  @Test
  void queryFreeBusy_when403RateLimited_throwsPlainRuntimeException() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    GoogleCalendarOAuthClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(FREE_BUSY_URL))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .body(
                    """
                        {"error": {"code": 403, "message": "User Rate Limit Exceeded",
                          "errors": [{"message": "User Rate Limit Exceeded", "domain": "usageLimits", "reason": "userRateLimitExceeded"}]}}
                        """)
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(
        () -> client.queryFreeBusy(
            UUID.randomUUID(),
            "at",
            Instant.parse("2026-08-20T00:00:00Z"),
            Instant.parse("2026-08-25T00:00:00Z")))
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(GoogleCalendarAuthException.class);
    serverHolder[0].verify();
  }
}
