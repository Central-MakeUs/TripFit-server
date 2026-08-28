package com.tripfit.tripfit.auth.oauth;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class KakaoTokenVerifier implements SocialTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(KakaoTokenVerifier.class);

  private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

  private final RestClient restClient;

  public KakaoTokenVerifier(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public SocialProvider getProvider() {
    return SocialProvider.KAKAO;
  }

  @Override
  public OAuthProfile verify(String token) {
    try {

      JsonNode response =
          restClient
              .get()
              .uri(KAKAO_USER_ME_URL)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (request, clientResponse) -> {

                    String body = readBodySafely(clientResponse);
                    SocialIntegrationLog.warn(
                        log,
                        SocialLogContext.of(
                            SocialProvider.KAKAO,
                            SocialIntegrationAction.LOGIN_USERINFO_FETCH)
                            .withHttpStatus(clientResponse.getStatusCode().value())
                            .withProviderError(null, body),
                        "Kakao user/me verification failed");
                    throw new TripFitException(
                        SocialErrorMessages.containsExpired(body)
                            ? AuthErrorCode.AUTH_SOCIAL_TOKEN_EXPIRED
                            : AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
                  })
              .body(JsonNode.class);

      if (response == null || !response.has("id")) {
        throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
      }
      String providerUserId = response.get("id").asText();
      String email = null;
      String nickname = null;
      String profileImageUrl = null;
      JsonNode kakaoAccount = response.get("kakao_account");
      if (kakaoAccount != null) {
        if (kakaoAccount.has("email")) {
          email = kakaoAccount.get("email").asText();
        }
        JsonNode profile = kakaoAccount.get("profile");
        if (profile != null) {
          if (profile.has("nickname")) {
            nickname = profile.get("nickname").asText();
          }
          if (profile.has("profile_image_url")) {
            profileImageUrl = profile.get("profile_image_url").asText();
          }
        }
      }
      return new OAuthProfile(
          SocialProvider.KAKAO, providerUserId, email, nickname, profileImageUrl, null);
    } catch (TripFitException exception) {

      throw exception;
    } catch (RestClientException exception) {

      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.KAKAO, SocialIntegrationAction.LOGIN_USERINFO_FETCH),
          "Kakao user/me API unreachable",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    } catch (Exception exception) {

      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.KAKAO, SocialIntegrationAction.LOGIN_TOKEN_VERIFY),
          "Kakao token verification failed unexpectedly",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
  }

  private String readBodySafely(ClientHttpResponse clientResponse) {
    try {
      return StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "<unreadable>";
    }
  }

}
