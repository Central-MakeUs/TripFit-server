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
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class KakaoUserInfoClient {

  private static final Logger log = LoggerFactory.getLogger(KakaoUserInfoClient.class);

  // 요청 처리 스레드 200개 중 카카오 호출이 차지할 수 있는 최대 개수(10%)다.
  // 카카오가 정상(수백 ms)일 때는 초당 수십 건 이상의 로그인을 처리할 수 있다.
  static final int CONCURRENCY_LIMIT = 20;

  private final RestClient restClient;

  private final OAuthProperties oAuthProperties;

  public KakaoUserInfoClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

  // 카카오 액세스 토큰으로 user/me를 조회한다. 동시에 20개까지만 호출하고, 그 이상은 기다리지 않고
  // InvocationRejectedException으로 바로 거절한다. 카카오가 느려져도 로그인 요청이 요청 처리 스레드를
  // 그 이상 붙잡지 않게 해, 카카오와 무관한 API가 계속 응답하도록 하기 위해서다.
  // 카카오가 오류로 응답하면 토큰 만료·무효를 구분한 TripFitException을 던진다.
  @ConcurrencyLimit(limit = CONCURRENCY_LIMIT, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
  public JsonNode fetchUserMe(String accessToken) {
    return restClient
        .get()
        .uri(oAuthProperties.getKakaoUserMeUrl())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
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
  }

  private String readBodySafely(ClientHttpResponse clientResponse) {
    try {
      return StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      return "<unreadable>";
    }
  }
}
