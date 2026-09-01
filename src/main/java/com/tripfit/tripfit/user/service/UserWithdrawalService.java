package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.service.AppleCredentialService;
import com.tripfit.tripfit.auth.service.GoogleLoginCredentialService;
import com.tripfit.tripfit.user.client.KakaoUnlinkClient;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class UserWithdrawalService {

  private static final Logger log = LoggerFactory.getLogger(UserWithdrawalService.class);

  private final UserLookupService userLookupService;

  private final GoogleCalendarService googleCalendarService;

  private final KakaoUnlinkClient kakaoUnlinkClient;

  private final AppleCredentialService appleCredentialService;

  private final GoogleLoginCredentialService googleLoginCredentialService;

  private final UserWithdrawalPersistenceService persistenceService;

  // 회원 탈퇴 처리를 수행합니다.
  // 외부 연동(Google Calendar, Kakao 등)을 먼저 해제한 뒤, 내부 DB의 관련 데이터를 삭제/익명화합니다.
  public void withdraw(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {

      return;
    }

    // 1. Google 캘린더 연동 및 Google 로그인 Credential 해제
    revokeGoogleCalendarIfConnected(userId);
    googleLoginCredentialService.revokeAndDeleteIfPresent(userId);
    // 2. 카카오 로그인 연동 해제
    unlinkKakaoIfProvider(user);
    // 3. Apple 로그인 Credential 해제
    appleCredentialService.revokeAndDeleteIfPresent(userId);

    // 4. 내부 DB 연관 데이터 삭제 및 개인정보 마스킹(Soft Delete)
    persistenceService.finalizeWithdrawal(userId);
  }

  private void revokeGoogleCalendarIfConnected(UUID userId) {
    try {
      googleCalendarService.revokeIfConnected(userId);
    } catch (Exception exception) {
      log.warn("Google Calendar credential revoke failed", exception);
    }
  }

  private void unlinkKakaoIfProvider(User user) {
    if (user.getProvider() == SocialProvider.KAKAO) {
      kakaoUnlinkClient.unlink(user.getSocialId());
    }
  }
}
