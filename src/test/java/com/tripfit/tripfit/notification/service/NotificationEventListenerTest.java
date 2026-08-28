package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.repository.NotificationHistoryRepository;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository.UserTokenView;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.event.AllMembersSubmittedEvent;
import com.tripfit.tripfit.trip.event.TripConfirmCanceledEvent;
import com.tripfit.tripfit.trip.event.TripConfirmedEvent;
import com.tripfit.tripfit.trip.event.TripInfoChangedEvent;
import com.tripfit.tripfit.trip.event.TripJoinCompletedEvent;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private NotificationHistoryRepository notificationHistoryRepository;

  @Mock
  private UserDeviceTokenRepository userDeviceTokenRepository;

  @Mock
  private FcmService fcmService;

  private NotificationEventListener listener;

  private User owner;

  private Trip trip;

  @BeforeEach
  void setUp() {
    listener =
        new NotificationEventListener(
            tripRepository,
            tripMemberRepository,
            userRepository,
            notificationHistoryRepository,
            userDeviceTokenRepository,
            fcmService);
    owner = user("owner-sub", "홍", "길동");
    trip =
        new Trip(
            owner,
            "제주 여행",
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            2,
            3,
            4,
            "ABCD12",
            TripStatus.ONGOING);
    trip.setId(TRIP_ID);
  }

  @Test
  void onTripJoinCompleted_notifiesOwnerOnly() {
    User joinedMember = user("member-sub", "김", "철수");
    UserTokenView tokenView = mock(UserTokenView.class);
    when(tokenView.getUserId()).thenReturn(owner.getId());
    when(tokenView.getToken()).thenReturn("token-1");
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    when(userRepository.findById(joinedMember.getId())).thenReturn(Optional.of(joinedMember));
    when(userDeviceTokenRepository.findUserIdAndTokenByUserIdIn(anyList()))
        .thenReturn(List.of(tokenView));

    listener.onTripJoinCompleted(new TripJoinCompletedEvent(TRIP_ID, joinedMember.getId()));

    ArgumentCaptor<List<NotificationHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(notificationHistoryRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getUser()).isEqualTo(owner);
    assertThat(captor.getValue().get(0).getBody()).contains("김철수님이 여행방에 참여했어요");

    Map<String, UUID> expectedHistoryIdByToken = new HashMap<>();
    expectedHistoryIdByToken.put("token-1", null);
    verify(fcmService)
        .sendMulticast(
            eq(expectedHistoryIdByToken),
            any(),
            any(),
            eq(LandingType.TRAVEL_ROOM_DETAIL),
            eq(TRIP_ID));
  }

  @Test
  void onTripJoinCompleted_ownerNotificationDisabled_skipsDispatch() {
    owner.applyProfilePatch(null, null, false);
    User joinedMember = user("member-sub", "김", "철수");
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    when(userRepository.findById(joinedMember.getId())).thenReturn(Optional.of(joinedMember));

    listener.onTripJoinCompleted(new TripJoinCompletedEvent(TRIP_ID, joinedMember.getId()));

    verify(notificationHistoryRepository, never()).saveAll(any());
    verify(fcmService, never()).sendMulticast(any(), any(), any(), any(), any());
  }

  @Test
  void onAllMembersSubmitted_notifiesOwner() {
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));

    listener.onAllMembersSubmitted(new AllMembersSubmittedEvent(TRIP_ID));

    ArgumentCaptor<List<NotificationHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(notificationHistoryRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getUser()).isEqualTo(owner);
  }

  @Test
  void onTripInfoChanged_notifiesMembersExcludingOwner() {
    User memberA = user("a-sub", "김", "철수");
    User memberB = user("b-sub", "이", "영희");
    TripMember ownerMembership =
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    TripMember memberAMembership =
        new TripMember(trip, memberA, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    TripMember memberBMembership =
        new TripMember(trip, memberB, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership, memberAMembership, memberBMembership));

    listener.onTripInfoChanged(new TripInfoChangedEvent(TRIP_ID));

    ArgumentCaptor<List<NotificationHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(notificationHistoryRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).extracting(NotificationHistory::getUser)
        .containsExactlyInAnyOrder(memberA, memberB);
  }

  @Test
  void onTripConfirmed_notifiesMembersExcludingOwner() {
    User memberA = user("a-sub", "김", "철수");
    User memberB = user("b-sub", "이", "영희");
    TripMember ownerMembership =
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    TripMember memberAMembership =
        new TripMember(trip, memberA, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    TripMember memberBMembership =
        new TripMember(trip, memberB, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership, memberAMembership, memberBMembership));

    listener.onTripConfirmed(new TripConfirmedEvent(TRIP_ID));

    ArgumentCaptor<List<NotificationHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(notificationHistoryRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).extracting(NotificationHistory::getUser)
        .containsExactlyInAnyOrder(memberA, memberB);
    assertThat(captor.getValue().get(0).getBody()).contains("여행 일정이 확정되었어요");
  }

  @Test
  void onTripConfirmCanceled_notifiesMembersExcludingOwner() {
    User memberA = user("a-sub", "김", "철수");
    TripMember ownerMembership =
        new TripMember(trip, owner, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    TripMember memberAMembership =
        new TripMember(trip, memberA, TripMemberRole.MEMBER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID))
        .thenReturn(List.of(ownerMembership, memberAMembership));

    listener.onTripConfirmCanceled(new TripConfirmCanceledEvent(TRIP_ID));

    ArgumentCaptor<List<NotificationHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(notificationHistoryRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).extracting(NotificationHistory::getUser)
        .containsExactly(memberA);
    assertThat(captor.getValue().get(0).getBody()).contains("확정된 여행 일정이 취소되었어요");
  }

  private static User user(String socialId, String lastName, String firstName) {
    User u = new User(socialId, SocialProvider.GOOGLE, socialId + "@example.com", "nick", null);
    u.setId(UUID.randomUUID());
    u.applyProfilePatch(firstName, lastName, null);
    return u;
  }
}
