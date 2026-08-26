package com.tripfit.tripfit.user.client;

import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoUnlinkClient {

  private static final Logger log = LoggerFactory.getLogger(KakaoUnlinkClient.class);

  private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

  private final RestClient restClient;

  private final OAuthProperties oAuthProperties;

  public KakaoUnlinkClient(RestClient restClient, OAuthProperties oAuthProperties) {
    this.restClient = restClient;
    this.oAuthProperties = oAuthProperties;
  }

  // Admin Key로 카카오 회원번호(socialId) 기준 연결 해제 — 사용자 access_token 저장 없이 탈퇴 시 best-effort 호출
  public void unlink(String socialId) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("target_id_type", "user_id");
    form.add("target_id", socialId);
    try {
      restClient
          .post()
          .uri(UNLINK_URL)
          .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + oAuthProperties.getKakaoAdminKey())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form)
          .retrieve()
          .toBodilessEntity();
    } catch (Exception exception) {
      // best-effort — provider 실패가 탈퇴 트랜잭션 자체를 막으면 안 됨. GoogleOAuthClient·AppleOAuthClient의
      // revokeRefreshToken과 동일하게 구조화 로그로 남겨야 Loki에서 provider·action 기준으로 조회할 수 있다
      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.KAKAO, SocialIntegrationAction.LOGIN_TOKEN_REVOKE),
          "Kakao unlink failed",
          exception);
    }
  }
}
