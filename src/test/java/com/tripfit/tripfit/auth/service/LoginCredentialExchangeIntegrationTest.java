package com.tripfit.tripfit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.oauth.GoogleOAuthClient;
import com.tripfit.tripfit.auth.oauth.OAuthProfile;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifier;
import com.tripfit.tripfit.auth.oauth.SocialTokenVerifierRegistry;
import com.tripfit.tripfit.auth.repository.GoogleLoginCredentialRepository;
import com.tripfit.tripfit.common.config.TestcontainersConfig;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.user.client.KakaoUnlinkClient;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserWithdrawalService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Apple·Google 인가 코드 교환을 로그인 응답 뒤로 미뤘을 때, 로그인이 교환을 기다리지 않고 탈퇴와 엇갈려도
// 탈퇴한 사용자의 credential이 남지 않는지 실제 MySQL로 확인한다.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class LoginCredentialExchangeIntegrationTest {

  @MockitoBean
  private GoogleOAuthClient googleOAuthClient;

  @MockitoBean
  private SocialTokenVerifierRegistry verifierRegistry;

  @MockitoBean
  private KakaoUnlinkClient kakaoUnlinkClient;

  @Autowired
  private AuthService authService;

  @Autowired
  private LoginCredentialExchangeDispatcher dispatcher;

  @Autowired
  private UserWithdrawalService userWithdrawalService;

  @Autowired
  private GoogleLoginCredentialPersistenceService googleLoginCredentialPersistenceService;

  @Autowired
  private GoogleLoginCredentialRepository googleLoginCredentialRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private SocialTokenCrypto tokenCrypto;

  // 토큰 서버가 3초 걸려도 로그인은 그 응답을 기다리지 않고, credential은 그 뒤에 저장된다.
  @Test
  void googleLogin_doesNotWaitForSlowTokenExchange() {
    String subject = "slow-exchange-" + UUID.randomUUID();
    SocialTokenVerifier verifier = mock(SocialTokenVerifier.class);
    when(verifierRegistry.getVerifier(SocialProvider.GOOGLE)).thenReturn(verifier);
    when(verifier.verify("id-token"))
        .thenReturn(
            new OAuthProfile(
                SocialProvider.GOOGLE, subject, subject + "@example.com", "nick", null, null));
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken(eq("auth-code"), any()))
        .thenAnswer(invocation -> {
          Thread.sleep(3_000);
          return "slow-refresh";
        });

    long startedAt = System.nanoTime();
    authService.login(SocialProvider.GOOGLE, "id-token", "auth-code", null);
    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

    assertThat(elapsedMillis).isLessThan(1_000);
    UUID userId =
        userRepository.findByProviderAndSocialId(SocialProvider.GOOGLE, subject).orElseThrow()
            .getId();
    awaitCredential(userId, true);
  }

  // 교환이 끝나기 전에 탈퇴가 커밋됐으면 저장하지 않고, 받은 토큰으로 Google 연결을 바로 끊는다.
  @Test
  void exchangeFinishingAfterWithdrawal_isNotSaved_andRevokesIssuedToken() {
    User user = createUser(SocialProvider.GOOGLE);
    user.scrubPiiForWithdrawal();
    userRepository.save(user);
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken(eq("auth-code"), any()))
        .thenReturn("late-refresh");

    dispatcher.submitGoogle(user.getId(), "auth-code", null);

    verify(googleOAuthClient, timeout(5_000)).revokeRefreshToken("late-refresh");
    assertThat(googleLoginCredentialRepository.findByUser_Id(user.getId())).isEmpty();
  }

  // 탈퇴의 첫 credential 확인이 끝난 뒤, DB 정리가 커밋되기 전에 교환이 저장을 마친 경우다.
  // 탈퇴는 DB 정리 뒤 한 번 더 확인하므로 그 credential을 폐기하고 지운다.
  @Test
  void credentialSavedDuringWithdrawal_isRevokedAndDeletedAfterFinalizing() {
    User user = createUser(SocialProvider.KAKAO);
    String ciphertext = tokenCrypto.encrypt("mid-withdrawal-refresh");
    // 카카오 연결 해제(첫 credential 확인 뒤, DB 정리 전) 시점에 교환 저장이 끝난 것처럼 만든다.
    doAnswer(invocation -> {
      assertThat(
          googleLoginCredentialPersistenceService.saveForActiveUser(user.getId(), ciphertext))
          .isTrue();
      return null;
    }).when(kakaoUnlinkClient).unlink(anyString());

    userWithdrawalService.withdraw(user.getId());

    verify(googleOAuthClient).revokeRefreshToken("mid-withdrawal-refresh");
    assertThat(googleLoginCredentialRepository.findByUser_Id(user.getId())).isEmpty();
  }

  // 탈퇴하지 않은 사용자에게는 정상적으로 저장되고 폐기 요청은 나가지 않는다.
  @Test
  void exchangeForActiveUser_savesCredentialWithoutRevoking() {
    User user = createUser(SocialProvider.GOOGLE);
    when(googleOAuthClient.exchangeAuthorizationCodeForRefreshToken(eq("auth-code"), any()))
        .thenReturn("active-refresh");

    dispatcher.submitGoogle(user.getId(), "auth-code", null);

    awaitCredential(user.getId(), true);
    verify(googleOAuthClient, never()).revokeRefreshToken(any());
  }

  private User createUser(SocialProvider provider) {
    String subject = "exchange-" + UUID.randomUUID();
    User user = new User(subject, provider, subject + "@example.com", "nick", null);
    user.applyProfilePatch("철수", "김", true);
    return userRepository.save(user);
  }

  private void awaitCredential(UUID userId, boolean present) {
    for (int i = 0; i < 100; i++) {
      if (googleLoginCredentialRepository.findByUser_Id(userId).isPresent() == present) {
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      }
    }
    assertThat(googleLoginCredentialRepository.findByUser_Id(userId).isPresent())
        .isEqualTo(present);
  }
}
