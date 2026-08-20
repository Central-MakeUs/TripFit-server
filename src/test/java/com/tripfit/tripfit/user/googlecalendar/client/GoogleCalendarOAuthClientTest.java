package com.tripfit.tripfit.user.googlecalendar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GoogleCalendarOAuthClientTest {

  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private GoogleCalendarOAuthClient newClient(MockRestServiceServer[] serverHolder) {
    RestClient.Builder builder = RestClient.builder();
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    OAuthProperties properties = new OAuthProperties();
    properties.setGoogleCalendarClientId("calendar-client-id");
    properties.setGoogleCalendarClientSecret("calendar-client-secret");
    return new GoogleCalendarOAuthClient(builder.build(), properties);
  }

  // Google 토큰 엔드포인트는 redirect_uri 파라미터가 없으면 네이티브 serverAuthCode에도 "Missing parameter:
  // redirect_uri" 400을 반환함(로그인용 GoogleOAuthClient에서 실계정 테스트로 확인된 동일 요구사항, #64) — 네이티브
  // 경로(redirectUri=null)는 빈 문자열로 보내는지 검증
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

  // 브라우저 리다이렉트 경로 — authorize 요청에 실제로 쓴 redirect_uri를 code 교환에 그대로 전달하는지 검증
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
}
