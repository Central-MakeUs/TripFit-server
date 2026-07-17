package com.tripfit.tripfit.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.dto.CreateTripRequest;
import com.tripfit.tripfit.trip.dto.JoinTripRequest;
import com.tripfit.tripfit.trip.dto.PatchTripRequest;
import com.tripfit.tripfit.trip.dto.UpdateTripPinRequest;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.RecommendationRepository;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.exception.UserErrorCode;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import com.tripfit.tripfit.user.service.UserProfileService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserProfileService userProfileService;

  @Mock
  private ScheduleService scheduleService;

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private RecommendationRepository recommendationRepository;

  @InjectMocks
  private TripService tripService;

  private User owner;

  private User member;

  private Trip trip;

  @BeforeEach
  void setUp() {
    owner = user(OWNER_ID, "홍", "길동");
    member = user(MEMBER_ID, "김", "철수");
    trip = ongoingTrip();
  }

  @Test
  void createTrip_issuesOwnerMemberAndInviteCode() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    when(tripRepository.existsByInviteCode(any())).thenReturn(false);
    when(tripRepository.save(any(Trip.class)))
        .thenAnswer(
            invocation -> {
              Trip saved = invocation.getArgument(0);
              saved.setId(TRIP_ID);
              return saved;
            });

    var response =
        tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "제주 3박4일",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                4,
                6,
                "제주"));

    assertThat(response.tripId()).isEqualTo(TRIP_ID);
    assertThat(response.status()).isEqualTo(TripStatus.ONGOING);
    assertThat(response.inviteCode()).hasSize(6);

    ArgumentCaptor<TripMember> memberCaptor = ArgumentCaptor.forClass(TripMember.class);
    verify(tripMemberRepository).save(memberCaptor.capture());
    assertThat(memberCaptor.getValue().getRole()).isEqualTo(TripMemberRole.OWNER);
    assertThat(memberCaptor.getValue().getStatus()).isEqualTo(TripMemberStatus.JOINED);
  }

  @Test
  void createTrip_rejectsNameOver15Chars() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

    assertThatThrownBy(
        () -> tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "가".repeat(16),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                4,
                6,
                null)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void createTrip_requiresProfileName() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    org.mockito.Mockito.doThrow(new TripFitException(UserErrorCode.PROFILE_NAME_REQUIRED))
        .when(userProfileService)
        .requireProfileNameComplete(owner);

    assertThatThrownBy(
        () -> tripService.createTrip(
            OWNER_ID,
            new CreateTripRequest(
                "제주",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                4,
                6,
                null)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(UserErrorCode.PROFILE_NAME_REQUIRED);
  }

  @Test
  void joinTrip_idempotentForExistingMemberOnConfirmedTrip() {
    trip.setStatus(TripStatus.CONFIRMED);
    TripMember existing = tripMember(member, TripMemberRole.MEMBER);
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.of(existing));
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(2L);
    when(
        tripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(
            TRIP_ID,
            TripMemberStatus.RESPONDED))
        .thenReturn(1L);

    var summary = tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234"));

    assertThat(summary.tripId()).isEqualTo(TRIP_ID);
    verify(tripMemberRepository, never()).save(any());
  }

  @Test
  void joinTrip_rejectsNewMemberOnConfirmedTrip() {
    trip.setStatus(TripStatus.CONFIRMED);
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_ALREADY_CONFIRMED);
  }

  @Test
  void joinTrip_rejectsWhenMemberFull() {
    when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
    when(tripRepository.findByInviteCodeAndDeletedAtIsNull("ABC234"))
        .thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, MEMBER_ID))
        .thenReturn(Optional.empty());
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(6L);

    assertThatThrownBy(() -> tripService.joinTrip(MEMBER_ID, new JoinTripRequest("ABC234")))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_MEMBER_FULL);
  }

  @Test
  void patchTrip_rejectsNonOwner() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));

    assertThatThrownBy(
        () -> tripService.patchTrip(
            TRIP_ID,
            MEMBER_ID,
            patchRequest()))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_FORBIDDEN);
  }

  @Test
  void patchTrip_rejectsWhenNotOngoing() {
    trip.setStatus(TripStatus.CONFIRMED);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));

    assertThatThrownBy(
        () -> tripService.patchTrip(
            TRIP_ID,
            OWNER_ID,
            patchRequest()))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_NOT_ONGOING);
  }

  @Test
  void patchTrip_deletesRecommendationsWhenRangeChanges() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    TripMember ownerMember = tripMember(owner, TripMemberRole.OWNER);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(ownerMember));
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(1L);
    when(
        tripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(
            TRIP_ID,
            TripMemberStatus.RESPONDED))
        .thenReturn(0L);

    tripService.patchTrip(
        TRIP_ID,
        OWNER_ID,
        new PatchTripRequest(
            "제주",
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 10),
            4,
            6,
            "제주"));

    verify(recommendationRepository).deleteByTripId(TRIP_ID);
  }

  @Test
  void submitSchedule_setsRespondedWhenRegularExists() {
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    TripMember membership = tripMember(owner, TripMemberRole.OWNER);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(membership));
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(1L);
    when(
        tripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(
            TRIP_ID,
            TripMemberStatus.RESPONDED))
        .thenReturn(1L);

    var summary = tripService.submitSchedule(TRIP_ID, OWNER_ID);

    assertThat(membership.getStatus()).isEqualTo(TripMemberStatus.RESPONDED);
    assertThat(summary.myMemberStatus()).isEqualTo(TripMemberStatus.RESPONDED);
    verify(scheduleService).requireRegularScheduleRegistered(OWNER_ID);
  }

  @Test
  void updatePin_togglesPinned() {
    TripMember membership = tripMember(owner, TripMemberRole.OWNER);
    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(membership));
    when(tripMemberRepository.countByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(1L);
    when(
        tripMemberRepository.countByTripIdAndStatusAndDeletedAtIsNull(
            TRIP_ID,
            TripMemberStatus.RESPONDED))
        .thenReturn(0L);

    var summary =
        tripService.updatePin(TRIP_ID, OWNER_ID, new UpdateTripPinRequest(true));

    assertThat(summary.pinned()).isTrue();
    assertThat(membership.isPinned()).isTrue();
  }

  @Test
  void listMembers_assignsDuplicateDisplayNames() {
    User dup1 = user(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"), "홍", "길동");
    User dup2 = user(UUID.fromString("550e8400-e29b-41d4-a716-446655440004"), "홍", "길동");
    TripMember m1 = tripMember(dup1, TripMemberRole.MEMBER);
    TripMember m2 = tripMember(dup2, TripMemberRole.MEMBER);
    m1.setJoinedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
    m2.setJoinedAt(LocalDateTime.of(2026, 7, 2, 10, 0));

    when(tripMemberRepository.findByTripIdAndUserIdAndDeletedAtIsNull(TRIP_ID, OWNER_ID))
        .thenReturn(Optional.of(tripMember(owner, TripMemberRole.OWNER)));
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(List.of(m1, m2));

    var response = tripService.listMembers(TRIP_ID, OWNER_ID);

    assertThat(response.members())
        .extracting(m -> m.displayName())
        .containsExactly("홍길동", "홍길동(2)");
  }

  private static PatchTripRequest patchRequest() {
    return new PatchTripRequest(
        "제주",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 10),
        4,
        6,
        "제주");
  }

  private Trip ongoingTrip() {
    Trip t =
        new Trip(
            owner,
            "제주 3박4일",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 10),
            4,
            6,
            "ABC234",
            TripStatus.ONGOING);
    t.setId(TRIP_ID);
    return t;
  }

  private TripMember tripMember(User user, TripMemberRole role) {
    TripMember tm =
        new TripMember(trip, user, role, TripMemberStatus.JOINED, LocalDateTime.now());
    return tm;
  }

  private static User user(UUID id, String lastName, String firstName) {
    User u = new User("sub-" + id, SocialProvider.GOOGLE, "u@example.com", "nick", null);
    u.setId(id);
    u.setLastName(lastName);
    u.setFirstName(firstName);
    return u;
  }
}
