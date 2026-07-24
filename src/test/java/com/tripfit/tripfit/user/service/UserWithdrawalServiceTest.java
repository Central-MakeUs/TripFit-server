package com.tripfit.tripfit.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.service.RefreshTokenService;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private UserRepository userRepository;

  @Mock
  private TripService tripService;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private GoogleCalendarCredentialRepository googleCalendarCredentialRepository;

  @Mock
  private GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository;

  @Mock
  private RefreshTokenService refreshTokenService;

  private UserWithdrawalService userWithdrawalService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    userWithdrawalService =
        new UserWithdrawalService(
            userRepository,
            tripService,
            personalScheduleRepository,
            regularScheduleRepository,
            googleCalendarCredentialRepository,
            googleCalendarBusyDayRepository,
            refreshTokenService);
  }

  @Test
  void withdraw_cascadesLeavesAndDeletesTrips() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    userWithdrawalService.withdraw(USER_ID);

    verify(tripService).leaveAllActiveTripsAsMember(USER_ID);
    verify(tripService).deleteAllOwnedActiveTrips(USER_ID);
  }

  @Test
  void withdraw_hardDeletesPersonalDataAndRevokesRefreshTokens() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    userWithdrawalService.withdraw(USER_ID);

    verify(personalScheduleRepository).deleteByUserId(USER_ID);
    verify(regularScheduleRepository).deleteByUserId(USER_ID);
    verify(googleCalendarCredentialRepository).deleteByUser_Id(USER_ID);
    verify(googleCalendarBusyDayRepository).deleteByUser_Id(USER_ID);
    verify(refreshTokenService).revokeAllForUser(USER_ID);
  }

  @Test
  void withdraw_softDeletesUserAndScrubsPii() {
    User user = user();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    userWithdrawalService.withdraw(USER_ID);

    assertThat(user.getDeletedAt()).isNotNull();
    assertThat(user.getEmail()).isNull();
    assertThat(user.getFirstName()).isNull();
    assertThat(user.getLastName()).isNull();
    assertThat(user.getNickname()).isNull();
    assertThat(user.getProfileImageUrl()).isNull();
    assertThat(user.isGoogleCalendarConnected()).isFalse();
    assertThat(user.getSocialId()).isEqualTo("google-sub");
    assertThat(user.getProvider()).isEqualTo(SocialProvider.GOOGLE);
  }

  @Test
  void withdraw_whenAlreadyWithdrawn_isIdempotentNoOp() {
    User user = user();
    user.setDeletedAt(LocalDateTime.now().minusDays(1));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

    userWithdrawalService.withdraw(USER_ID);

    verify(tripService, never()).leaveAllActiveTripsAsMember(any());
    verify(tripService, never()).deleteAllOwnedActiveTrips(any());
    verify(personalScheduleRepository, never()).deleteByUserId(any());
  }

  private static User user() {
    User user =
        new User(
            "google-sub",
            SocialProvider.GOOGLE,
            "user@example.com",
            "닉네임",
            "https://example.com/profile.png");
    user.setId(USER_ID);
    user.setFirstName("길동");
    user.setLastName("홍");
    user.setGoogleCalendarConnected(true);
    return user;
  }
}
