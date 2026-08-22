package com.tripfit.tripfit.user.service;

import com.tripfit.tripfit.auth.service.AppleCredentialService;
import com.tripfit.tripfit.auth.service.GoogleLoginCredentialService;
import com.tripfit.tripfit.auth.service.RefreshTokenService;
import com.tripfit.tripfit.common.security.SocialTokenCrypto;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.client.KakaoUnlinkClient;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleCalendarOAuthClient;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// 회원 탈퇴 유스케이스 — cascade(참여 방 나가기·소유 방 삭제) → 소셜 provider revoke(best-effort) → 개인 데이터 hard delete →
// User soft delete·PII 스크럽
public class UserWithdrawalService {

  private static final Logger log = LoggerFactory.getLogger(UserWithdrawalService.class);

  private final UserLookupService userLookupService;

  private final TripService tripService;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final GoogleCalendarCredentialRepository googleCalendarCredentialRepository;

  private final GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository;

  private final GoogleCalendarOAuthClient googleCalendarOAuthClient;

  private final SocialTokenCrypto tokenCrypto;

  private final KakaoUnlinkClient kakaoUnlinkClient;

  private final AppleCredentialService appleCredentialService;

  private final GoogleLoginCredentialService googleLoginCredentialService;

  private final RefreshTokenService refreshTokenService;

  public UserWithdrawalService(
      UserLookupService userLookupService,
      TripService tripService,
      PersonalScheduleRepository personalScheduleRepository,
      RegularScheduleRepository regularScheduleRepository,
      GoogleCalendarCredentialRepository googleCalendarCredentialRepository,
      GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository,
      GoogleCalendarOAuthClient googleCalendarOAuthClient,
      SocialTokenCrypto tokenCrypto,
      KakaoUnlinkClient kakaoUnlinkClient,
      AppleCredentialService appleCredentialService,
      GoogleLoginCredentialService googleLoginCredentialService,
      RefreshTokenService refreshTokenService) {
    this.userLookupService = userLookupService;
    this.tripService = tripService;
    this.personalScheduleRepository = personalScheduleRepository;
    this.regularScheduleRepository = regularScheduleRepository;
    this.googleCalendarCredentialRepository = googleCalendarCredentialRepository;
    this.googleCalendarBusyDayRepository = googleCalendarBusyDayRepository;
    this.googleCalendarOAuthClient = googleCalendarOAuthClient;
    this.tokenCrypto = tokenCrypto;
    this.kakaoUnlinkClient = kakaoUnlinkClient;
    this.appleCredentialService = appleCredentialService;
    this.googleLoginCredentialService = googleLoginCredentialService;
    this.refreshTokenService = refreshTokenService;
  }

  // 차단 없이 항상 진행 — 참여 방 자동 나가기·소유 방 자동 삭제 후 개인 데이터 hard delete, User는 soft delete+PII 스크럽
  @Transactional
  public void withdraw(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {
      // 이미 탈퇴한 계정(액세스 토큰 만료 전 재호출) — 중복 처리 없이 idempotent 종료
      return;
    }

    // 1. cascade: MEMBER인 활성 방 전부 나가기 → OWNER인 활성 방 전부 삭제
    tripService.leaveAllActiveTripsAsMember(userId);
    tripService.deleteAllOwnedActiveTrips(userId);

    // 2. 소셜 provider revoke·unlink (best-effort — 실패해도 탈퇴 자체는 계속 진행)
    revokeGoogleCalendarIfConnected(userId);
    googleLoginCredentialService.revokeAndDeleteIfPresent(userId);
    unlinkKakaoIfProvider(user);
    appleCredentialService.revokeAndDeleteIfPresent(userId);

    // 3. 개인 전용 데이터 hard delete
    personalScheduleRepository.deleteByUserId(userId);
    regularScheduleRepository.deleteByUserId(userId);
    googleCalendarCredentialRepository.deleteByUser_Id(userId);
    googleCalendarBusyDayRepository.deleteByUser_Id(userId);
    refreshTokenService.revokeAllForUser(userId);

    // 4. User soft delete + PII 스크럽 — socialId·provider·id는 FK 무결성·재로그인 차단 판별을 위해 유지
    user.setDeletedAt(LocalDateTime.now());
    user.setEmail(null);
    user.setFirstName(null);
    user.setLastName(null);
    user.setNickname(null);
    user.setProfileImageUrl(null);
    user.setGoogleCalendarConnected(false);
  }

  // Google Calendar 연동돼 있으면 credential hard delete 전에 refresh token revoke 호출 (best-effort,
  // disconnect()와 동일 패턴) — 복호화 실패 등 예상 밖 오류도 탈퇴 자체를 막지 않도록 여기서 흡수함
  private void revokeGoogleCalendarIfConnected(UUID userId) {
    googleCalendarCredentialRepository
        .findByUser_Id(userId)
        .ifPresent(
            (GoogleCalendarCredential credential) -> {
              try {
                String refreshToken =
                    tokenCrypto.decrypt(credential.getRefreshTokenCiphertext());
                googleCalendarOAuthClient.revokeRefreshToken(refreshToken);
              } catch (Exception exception) {
                log.warn("Google Calendar credential revoke failed", exception);
              }
            });
  }

  // 카카오로 가입한 사용자면 Admin Key 기반 unlink 호출 (best-effort, 사용자 access_token 저장 불필요)
  private void unlinkKakaoIfProvider(User user) {
    if (user.getProvider() == SocialProvider.KAKAO) {
      kakaoUnlinkClient.unlink(user.getSocialId());
    }
  }
}
