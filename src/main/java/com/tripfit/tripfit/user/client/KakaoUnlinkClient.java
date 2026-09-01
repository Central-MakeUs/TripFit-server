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

  // 유저 탈퇴 시 카카오서버에 연동 해제(Unlink)를 요청합니다.
  // API 스펙 참조: https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api#unlink
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

      SocialIntegrationLog.warn(
          log,
          SocialLogContext.of(SocialProvider.KAKAO, SocialIntegrationAction.LOGIN_TOKEN_REVOKE),
          "Kakao unlink failed",
          exception);
    }
  }
}
