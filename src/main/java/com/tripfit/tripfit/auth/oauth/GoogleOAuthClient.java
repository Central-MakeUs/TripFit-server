package com.tripfit.tripfit.auth.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

// Google 로그인 authorization code → refresh token 교환·revoke — 탈퇴 시 로그인 동의 자체를 revoke하기 위한
// 전용 클라이언트(Calendar 연동용 GoogleCalendarOAuthClient와는 별개, user/googlecalendar 패키지 역방향 의존을 피함)
@Component
public class GoogleOAuthClient {

  private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);

  private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

  private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

  private final RestClient restClient;

  private final OAuthProperties oAuthProperties;

  public GoogleOAuthClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

  // 로그인 시 받은 authorization code를 refresh token으로 교환 — Google은 최초 동의 때만 refresh_token을 내려주므로
  // 재로그인 등 정상 케이스에서 null을 반환할 수 있음(예외 아님, 호출부가 null이면 저장을 스킵해야 함). client_id/secret은
  // Calendar 연동과 동일한 Web Client ID 값을 재사용(로그인·Calendar 전용 분리는 별도 검토)
  public String exchangeAuthorizationCodeForRefreshToken(String authorizationCode) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", authorizationCode);
    form.add("client_id", oAuthProperties.getGoogleClientId());
    form.add("client_secret", oAuthProperties.getGoogleClientSecret());
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
                  throw new IllegalStateException(
                      "Google token endpoint error: " + clientResponse.getStatusCode());
                })
            .body(JsonNode.class);
    if (response == null) {
      throw new IllegalStateException("Google token response missing body");
    }
    if (!response.hasNonNull("refresh_token")) {
      return null;
    }
    return response.get("refresh_token").asText();
  }

  // refresh token revoke (best-effort) — Google revoke 엔드포인트는 client_id/secret 없이 토큰만 요구(Apple과 다름)
  public void revokeRefreshToken(String refreshToken) {
    try {
      restClient
          .post()
          .uri(REVOKE_URL + "?token=" + refreshToken)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception exception) {
      log.warn("Google login refresh token revoke failed", exception);
    }
  }
}
