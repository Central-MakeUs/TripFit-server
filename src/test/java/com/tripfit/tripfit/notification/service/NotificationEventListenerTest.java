package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.event.AllMembersSubmittedEvent;
import com.tripfit.tripfit.notification.event.TripInfoChangedEvent;
import com.tripfit.tripfit.notification.event.TripJoinCompletedEvent;
import com.tripfit.tripfit.notification.repository.NotificationHistoryRepository;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    when(userRepository.findById(joinedMember.getId())).thenReturn(Optional.of(joinedMember));
    when(userDeviceTokenRepository.findTokensByUserIdIn(anyList())).thenReturn(List.of("token-1"));

    listener.onTripJoinCompleted(new TripJoinCompletedEvent(TRIP_ID, joinedMember.getId()));

    ArgumentCaptor<List<NotificationHistory>> captor = ArgumentCaptor.forClass(List.class);
    verify(notificationHistoryRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getUser()).isEqualTo(owner);
    assertThat(captor.getValue().get(0).getBody()).contains("김철수님이 여행방에 참여했어요");
    verify(fcmService)
        .sendMulticast(eq(List.of("token-1")), any(), any(), eq(LandingType.TRAVEL_ROOM_DETAIL));
  }

  @Test
  void onTripJoinCompleted_ownerNotificationDisabled_skipsDispatch() {
    owner.setNotificationEnabled(false);
    User joinedMember = user("member-sub", "김", "철수");
    when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
    when(userRepository.findById(joinedMember.getId())).thenReturn(Optional.of(joinedMember));

    listener.onTripJoinCompleted(new TripJoinCompletedEvent(TRIP_ID, joinedMember.getId()));

    verify(notificationHistoryRepository, never()).saveAll(any());
    verify(fcmService, never()).sendMulticast(any(), any(), any(), any());
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

  private static User user(String socialId, String lastName, String firstName) {
    User u = new User(socialId, SocialProvider.GOOGLE, socialId + "@example.com", "nick", null);
    u.setId(UUID.randomUUID());
    u.setLastName(lastName);
    u.setFirstName(firstName);
    return u;
  }
}
