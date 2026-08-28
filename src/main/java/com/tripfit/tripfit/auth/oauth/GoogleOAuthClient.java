package com.tripfit.tripfit.auth.oauth;

import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

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

  public String exchangeAuthorizationCodeForRefreshToken(
      String authorizationCode,
      String redirectUri) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("code", authorizationCode);
    form.add("client_id", oAuthProperties.getGoogleClientId());
    form.add("client_secret", oAuthProperties.getGoogleClientSecret());

    form.add("redirect_uri", redirectUri == null ? "" : redirectUri);
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
                  String body =
                      StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
                  SocialIntegrationLog.warn(
                      log,
                      SocialLogContext.of(
                          SocialProvider.GOOGLE,
                          SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
                          .withHttpStatus(clientResponse.getStatusCode().value())
                          .withProviderError(null, body),
                      "Google login authorization code exchange failed");
                  throw new IllegalStateException(
                      "Google token endpoint error: "
                          + clientResponse.getStatusCode()
                          + " body="
                          + body);
                })
            .body(JsonNode.class);
    if (response == null) {
      throw new IllegalStateException("Google token response missing body");
    }
    if (response.hasNonNull("scope")) {
      SocialIntegrationLog.info(
          log,
          SocialLogContext
              .of(SocialProvider.GOOGLE, SocialIntegrationAction.LOGIN_CREDENTIAL_EXCHANGE)
              .withGrantedScope(response.get("scope").asText()),
          "Google login authorization code exchange succeeded");
    }
    if (!response.hasNonNull("refresh_token")) {
      return null;
    }
    return response.get("refresh_token").asText();
  }

  public void revokeRefreshToken(String refreshToken) {
    try {
      restClient
          .post()
          .uri(REVOKE_URL + "?token=" + refreshToken)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception exception) {
      SocialLogContext context =
          SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.LOGIN_TOKEN_REVOKE);
      if (exception instanceof RestClientResponseException restException) {
        context =
            context
                .withHttpStatus(restException.getStatusCode().value())
                .withProviderError(null, restException.getResponseBodyAsString());
      }
      SocialIntegrationLog
          .warn(log, context, "Google login refresh token revoke failed", exception);
    }
  }
}
