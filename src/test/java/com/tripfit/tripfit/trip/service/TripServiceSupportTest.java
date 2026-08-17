package com.tripfit.tripfit.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.dto.MemberPreviewResponse;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.trip.repository.projection.TripMemberPreviewProjection;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TripServiceSupportTest {

  @Test
  void resolveDurationDays_bothNull_returnsNull() {
    assertThat(TripServiceSupport.resolveDurationDays(null, null)).isNull();
  }

  @Test
  void resolveDurationDays_nightsPlusOne_returnsDays() {
    assertThat(TripServiceSupport.resolveDurationDays(3, 4)).isEqualTo(4);
  }

  @Test
  void resolveDurationDays_nightsPlusTwo_returnsDays() {
    assertThat(TripServiceSupport.resolveDurationDays(3, 5)).isEqualTo(5);
  }

  @Test
  void resolveDurationDays_dayTrip_allowsOneOrTwoDays() {
    assertThat(TripServiceSupport.resolveDurationDays(0, 1)).isEqualTo(1);
    assertThat(TripServiceSupport.resolveDurationDays(0, 2)).isEqualTo(2);
  }

  @Test
  void resolveDurationDays_nightsPlusThree_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(3, 6))
        .isInstanceOf(TripFitException.class)
        .extracting(e -> ((TripFitException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void resolveDurationDays_daysLessThanNightsPlusOne_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(3, 3))
        .isInstanceOf(TripFitException.class);
  }

  @Test
  void resolveDurationDays_onlyOneSideNull_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(3, null))
        .isInstanceOf(TripFitException.class);
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(null, 4))
        .isInstanceOf(TripFitException.class);
  }

  @Test
  void resolveDurationDays_negativeNights_throwsInvalidInput() {
    assertThatThrownBy(() -> TripServiceSupport.resolveDurationDays(-1, 1))
        .isInstanceOf(TripFitException.class);
  }

  @Test
  void loadMemberPreviewsByTripIds_assignsDedupedDisplayNamePerTrip() {
    TripRepository tripRepository = mock(TripRepository.class);
    TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    UserLookupService userLookupService = mock(UserLookupService.class);
    UserRepository userRepository = mock(UserRepository.class);
    TripServiceSupport support =
        new TripServiceSupport(
            tripRepository, tripMemberRepository, userLookupService, userRepository);

    UUID tripId = UUID.randomUUID();
    User owner = user("민서", null);
    User duplicateNameMember = user("민서", null);

    when(tripMemberRepository.findMemberPreviewsByTripIds(List.of(tripId)))
        .thenReturn(
            List.of(
                previewProjection(tripId, owner.getId(), "OWNER"),
                previewProjection(tripId, duplicateNameMember.getId(), "MEMBER")));
    when(userRepository.findAllById(any())).thenReturn(List.of(owner, duplicateNameMember));

    Map<UUID, List<MemberPreviewResponse>> result =
        support.loadMemberPreviewsByTripIds(List.of(tripId));

    assertThat(result.get(tripId))
        .extracting(MemberPreviewResponse::displayName)
        .containsExactly("민서", "민서(2)");
  }

  @Test
  void loadMemberPreviewsByTripIds_usesFirstNameOnly_whenProfileNameComplete() {
    TripRepository tripRepository = mock(TripRepository.class);
    TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    UserLookupService userLookupService = mock(UserLookupService.class);
    UserRepository userRepository = mock(UserRepository.class);
    TripServiceSupport support =
        new TripServiceSupport(
            tripRepository, tripMemberRepository, userLookupService, userRepository);

    UUID tripId = UUID.randomUUID();
    User owner = userWithFullName("홍", "길동");

    when(tripMemberRepository.findMemberPreviewsByTripIds(List.of(tripId)))
        .thenReturn(List.of(previewProjection(tripId, owner.getId(), "OWNER")));
    when(userRepository.findAllById(any())).thenReturn(List.of(owner));

    Map<UUID, List<MemberPreviewResponse>> result =
        support.loadMemberPreviewsByTripIds(List.of(tripId));

    assertThat(result.get(tripId))
        .extracting(MemberPreviewResponse::displayName)
        .containsExactly("길동");
  }

  private static User user(String nickname, String profileImageUrl) {
    User u = new User("sub-" + UUID.randomUUID(), SocialProvider.GOOGLE, "u@example.com", nickname,
        profileImageUrl);
    u.setId(UUID.randomUUID());
    return u;
  }

  private static User userWithFullName(String lastName, String firstName) {
    User u =
        new User("sub-" + UUID.randomUUID(), SocialProvider.GOOGLE, "u@example.com", null, null);
    u.setId(UUID.randomUUID());
    u.setLastName(lastName);
    u.setFirstName(firstName);
    return u;
  }

  private static TripMemberPreviewProjection previewProjection(
      UUID tripId,
      UUID userId,
      String role) {
    return new TripMemberPreviewProjection() {
      @Override
      public UUID getTripId() {
        return tripId;
      }

      @Override
      public UUID getUserId() {
        return userId;
      }

      @Override
      public String getProfileImageUrl() {
        return null;
      }

      @Override
      public String getRole() {
        return role;
      }
    };
  }
}
