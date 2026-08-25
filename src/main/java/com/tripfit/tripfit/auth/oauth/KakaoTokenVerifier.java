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
                    // 카카오 원본 에러 응답을 남겨 만료/무효/앱 설정 오류를 사후에 구분 가능하게 함
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

      // 2. 응답 본문에서 필수 식별자와 선택 이메일을 추출함
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
      // 비즈니스 검증에서 만든 인증 예외는 그대로 상위로 전달함
      throw exception;
    } catch (RestClientException exception) {
      // 카카오 API 자체에 응답을 못 받은 경우(타임아웃·연결 실패) — 토큰 문제가 아니라 provider 장애
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.KAKAO, SocialIntegrationAction.LOGIN_USERINFO_FETCH),
          "Kakao user/me API unreachable",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
    } catch (Exception exception) {
      // JSON 파싱 등 그 외 실패 원인을 로그로 남기고 무효 토큰으로 통일
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.KAKAO, SocialIntegrationAction.LOGIN_TOKEN_VERIFY),
          "Kakao token verification failed unexpectedly",
          exception);
      throw new TripFitException(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
    }
  }

  // onStatus 핸들러에서 카카오 에러 응답 본문을 로그용으로 읽음 — 실패해도 검증 흐름은 계속됨
  private String readBodySafely(ClientHttpResponse clientResponse) {
    try {
      return StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "<unreadable>";
    }
  }

}
