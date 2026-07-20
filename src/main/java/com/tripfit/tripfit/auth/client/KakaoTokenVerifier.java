package com.tripfit.tripfit.auth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoTokenVerifier implements SocialTokenVerifier {

  private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

  private final RestClient restClient;

  public KakaoTokenVerifier(RestClient restClient) {
    this.restClient = restClient;
  }

  @Override
  public SocialProvider getProvider() {
    return SocialProvider.KAKAO;
  }

  // 카카오 사용자 조회 API로 액세스 토큰을 검증하고 사용자 프로필을 추출함
  @Override
  public OAuthProfile verify(String token) {
    try {
      // 1. 카카오 사용자 정보 API를 호출해 토큰 유효성을 확인함
      JsonNode response =
          restClient
              .get()
              .uri(KAKAO_USER_ME_URL)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
              .retrieve()
              .onStatus(
                  HttpStatusCode::isError,
                  (request, clientResponse) -> {
                    throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
                  })
              .body(JsonNode.class);

      // 2. 응답 본문에서 필수 식별자와 선택 이메일을 추출함
      if (response == null || !response.has("id")) {
        throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
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
          SocialProvider.KAKAO, providerUserId, email, nickname, profileImageUrl);
    } catch (TripFitException exception) {
      // 비즈니스 검증에서 만든 인증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (Exception exception) {
      // 카카오 API·JSON 파싱 실패는 클라이언트 토큰 오류로 통일
      throw new TripFitException(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
  }
}
