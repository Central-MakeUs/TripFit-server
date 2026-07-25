package com.tripfit.tripfit.user.service;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.dto.UpdateMyPageRequest;
import com.tripfit.tripfit.user.dto.UpdateProfileRequest;
import com.tripfit.tripfit.user.dto.UserSummaryResponse;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

  @Mock
  private UserLookupService userLookupService;

  @Mock
  private UserSummaryService userSummaryService;

  @InjectMocks
  private UserProfileService userProfileService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
  }

  @Test
  void updateProfile_savesFirstAndLastName() {
    when(userLookupService.requireUser(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")))
        .thenReturn(user);
    when(userSummaryService.toSummary(user))
        .thenReturn(
            new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                "길동",
                "홍",
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                false,
                false,
                false));
    UserSummaryResponse response =
        userProfileService.updateProfile(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
            new UpdateProfileRequest("길동", "홍"));

    assertThat(user.getFirstName()).isEqualTo("길동");
    assertThat(user.getLastName()).isEqualTo("홍");
    assertThat(response.firstName()).isEqualTo("길동");
    assertThat(response.lastName()).isEqualTo("홍");
  }

  @Test
  void updateMyPage_savesFirstAndLastName() {
    user.setFirstName("길동");
    user.setLastName("홍");
    when(userLookupService.requireUser(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")))
        .thenReturn(user);
    when(userSummaryService.toSummary(user))
        .thenReturn(
            new UserSummaryResponse(
                user.getId(),
                user.getEmail(),
                "철수",
                "김",
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getProvider(),
                false,
                false,
                false));
    UserSummaryResponse response =
        userProfileService.updateMyPage(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
            new UpdateMyPageRequest("철수", "김"));

    assertThat(user.getFirstName()).isEqualTo("철수");
    assertThat(user.getLastName()).isEqualTo("김");
    assertThat(response.firstName()).isEqualTo("철수");
    assertThat(response.lastName()).isEqualTo("김");
  }

  @Test
  void requireProfileNameComplete_whenMissing_throws403() {
    assertThatThrownBy(() -> userProfileService.requireProfileNameComplete(user))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(UserErrorCode.PROFILE_NAME_REQUIRED);
  }

  @Test
  void requireProfileNameComplete_whenPresent_passes() {
    user.setFirstName("길동");
    user.setLastName("홍");

    userProfileService.requireProfileNameComplete(user);
  }

  @Test
  void updateProfile_whenUserMissing_throwsForbidden() {
    UUID missingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
    when(userLookupService.requireUser(missingId))
        .thenThrow(new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));

    assertThatThrownBy(
        () -> userProfileService.updateProfile(missingId, new UpdateProfileRequest("길동", "홍")))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_FORBIDDEN);
  }
}
