package com.tripfit.tripfit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.domain.NotificationType;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.repository.NotificationHistoryRepository;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private NotificationHistoryRepository notificationHistoryRepository;

  @InjectMocks
  private NotificationQueryService notificationQueryService;

  @Test
  void listRecent_mapsHistoriesToResponses() {
    User user = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    NotificationHistory history =
        new NotificationHistory(
            user,
            null,
            NotificationType.SCHEDULE_REMINDER,
            "일정 업데이트 알림",
            "8월 일정을 업데이트해보세요.",
            LandingType.SCHEDULE_MANAGEMENT,
            LocalDateTime.now());
    when(
        notificationHistoryRepository.findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
            any(),
            any()))
        .thenReturn(List.of(history));

    var responses = notificationQueryService.listRecent(USER_ID);

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).type()).isEqualTo(NotificationType.SCHEDULE_REMINDER);
    assertThat(responses.get(0).tripId()).isNull();
    assertThat(responses.get(0).roomName()).isNull();
  }

  @Test
  void listRecent_withTrip_mapsRoomName() {
    User user = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    Trip trip =
        new Trip(
            user,
            "제주도 3박4일",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 10),
            3,
            4,
            6,
            "ABC123",
            TripStatus.ONGOING);
    NotificationHistory history =
        new NotificationHistory(
            user,
            trip,
            NotificationType.JOIN_COMPLETED,
            "여행방 참여 알림",
            "홍길동님이 여행방에 참여했어요! 참여 현황을 확인해보세요.",
            LandingType.TRAVEL_ROOM_DETAIL,
            LocalDateTime.now());
    when(
        notificationHistoryRepository.findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(
            any(),
            any()))
        .thenReturn(List.of(history));

    var responses = notificationQueryService.listRecent(USER_ID);

    assertThat(responses.get(0).roomName()).isEqualTo("제주도 3박4일");
  }

  @Test
  void markRead_notOwned_throwsNotFound() {
    UUID notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
    when(notificationHistoryRepository.findByIdAndUser_Id(notificationId, USER_ID))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> notificationQueryService.markRead(USER_ID, notificationId))
        .isInstanceOf(TripFitException.class)
        .extracting(exception -> ((TripFitException) exception).getErrorCode())
        .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
  }

  @Test
  void markRead_owned_marksRead() {
    User user = new User("sub", SocialProvider.GOOGLE, "u@example.com", "nick", null);
    NotificationHistory history =
        new NotificationHistory(
            user,
            null,
            NotificationType.SCHEDULE_REMINDER,
            "일정 업데이트 알림",
            "8월 일정을 업데이트해보세요.",
            LandingType.SCHEDULE_MANAGEMENT,
            LocalDateTime.now());
    UUID notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");
    when(notificationHistoryRepository.findByIdAndUser_Id(notificationId, USER_ID))
        .thenReturn(Optional.of(history));

    notificationQueryService.markRead(USER_ID, notificationId);

    assertThat(history.isRead()).isTrue();
    assertThat(history.getReadAt()).isNotNull();
  }
}
