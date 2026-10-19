package com.tripfit.tripfit.auth.oauth;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class KakaoTokenVerifier implements SocialTokenVerifier {

  private static final Logger log = LoggerFactory.getLogger(KakaoTokenVerifier.class);

  private final KakaoUserInfoClient kakaoUserInfoClient;

  public KakaoTokenVerifier(KakaoUserInfoClient kakaoUserInfoClient) {
    this.kakaoUserInfoClient = kakaoUserInfoClient;
  }

  @Override
  public SocialProvider getProvider() {
    return SocialProvider.KAKAO;
  }

  @Override
  public OAuthProfile verify(String token) {
    try {

      JsonNode response = kakaoUserInfoClient.fetchUserMe(token);

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
    } catch (InvocationRejectedException exception) {
      // 카카오 동시 호출이 상한에 걸린 경우다. 카카오가 느려 호출이 쌓인 상황이라 연결 실패와 같은 503으로 안내한다.
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.KAKAO, SocialIntegrationAction.LOGIN_USERINFO_FETCH),
          "Kakao user/me concurrency limit reached",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
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
}
