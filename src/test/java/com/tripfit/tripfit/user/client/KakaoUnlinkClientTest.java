package com.tripfit.tripfit.user.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tripfit.tripfit.auth.oauth.OAuthProperties;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoUnlinkClientTest {

  private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

  private KakaoUnlinkClient newClient(MockRestServiceServer[] serverHolder) {
    RestClient.Builder builder = RestClient.builder();
    serverHolder[0] = MockRestServiceServer.bindTo(builder).build();
    OAuthProperties oAuthProperties = new OAuthProperties();
    oAuthProperties.setKakaoAdminKey("test-admin-key");
    return new KakaoUnlinkClient(builder.build(), oAuthProperties);
  }

  @Test
  void unlink_success_callsUnlinkEndpointWithAdminKey() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoUnlinkClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(UNLINK_URL))
        .andExpect(header("Authorization", "KakaoAK test-admin-key"))
        .andRespond(withSuccess("""
            {"id": 12345}
            """, MediaType.APPLICATION_JSON));

    client.unlink("12345");

    serverHolder[0].verify();
  }

  @Test
  void unlink_providerError_doesNotThrow() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoUnlinkClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(UNLINK_URL))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("""
            {"msg":"user not found","code":-401}
            """).contentType(MediaType.APPLICATION_JSON));

    assertThatCode(() -> client.unlink("unknown-id")).doesNotThrowAnyException();
  }

  @Test
  void unlink_providerUnreachable_doesNotThrow() {
    MockRestServiceServer[] serverHolder = new MockRestServiceServer[1];
    KakaoUnlinkClient client = newClient(serverHolder);
    serverHolder[0]
        .expect(requestTo(UNLINK_URL))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });

    assertThatCode(() -> client.unlink("12345")).doesNotThrowAnyException();
  }
}
