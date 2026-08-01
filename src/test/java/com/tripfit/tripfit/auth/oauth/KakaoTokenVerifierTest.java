package com.tripfit.tripfit.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoTokenVerifierTest {

  private static final String KAKAO_USER_ME_URL = "https://kapi.kakao.com/v2/user/me";

  private KakaoTokenVerifier newVerifier(MockRestServiceServer[] serverHolder) {
    // 프로덕션 RestClient 빈은 Boot 자동설정이 Jackson2 컨버터를 등록해주지만,
    // 컨텍스트 없는 단위 테스트에서는 JsonNode(Jackson2) 역직렬화를 위해 명시적으로 추가해야 함
    RestClient.Builder builder =
        RestClient.builder()
            .messageConverters(
                converters -> converters.add(0, new MappingJackson2HttpMessageConverter()));
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    return new KakaoTokenVerifier(builder.build());
  }

  @Test
  void verify_validToken_returnsProfile() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoTokenVerifier verifier = newVerifier(serverHolder);
    serverHolder[0]
        .expect(requestTo(KAKAO_USER_ME_URL))
        .andRespond(
            withSuccess(
                """
                    {"id": 12345, "kakao_account": {"email": "user@example.com"}}
                    """,
                MediaType.APPLICATION_JSON));

    OAuthProfile profile = verifier.verify("valid-token");

    assertThat(profile.providerUserId()).isEqualTo("12345");
    assertThat(profile.email()).isEqualTo("user@example.com");
  }

  @Test
  void verify_expiredToken_throwsSocialTokenExpired() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoTokenVerifier verifier = newVerifier(serverHolder);
    serverHolder[0]
        .expect(requestTo(KAKAO_USER_ME_URL))
        .andRespond(
            withStatus(HttpStatus.UNAUTHORIZED)
                .body("""
                    {"msg":"this access token is already expired","code":-401}
                    """)
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> verifier.verify("expired-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_SOCIAL_TOKEN_EXPIRED);
  }

  @Test
  void verify_invalidToken_throwsSocialTokenInvalid() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoTokenVerifier verifier = newVerifier(serverHolder);
    serverHolder[0]
        .expect(requestTo(KAKAO_USER_ME_URL))
        .andRespond(
            withStatus(HttpStatus.UNAUTHORIZED)
                .body("""
                    {"msg":"this access token does not exist","code":-401}
                    """)
                .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> verifier.verify("invalid-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
  }

  @Test
  void verify_missingIdField_throwsSocialTokenInvalid() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoTokenVerifier verifier = newVerifier(serverHolder);
    serverHolder[0]
        .expect(requestTo(KAKAO_USER_ME_URL))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> verifier.verify("weird-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_SOCIAL_TOKEN_INVALID);
  }

  @Test
  void verify_providerUnreachable_throwsSocialProviderUnavailable() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoTokenVerifier verifier = newVerifier(serverHolder);
    serverHolder[0]
        .expect(requestTo(KAKAO_USER_ME_URL))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });

    assertThatThrownBy(() -> verifier.verify("any-token"))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
  }
}
