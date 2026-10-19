package com.tripfit.tripfit.notification.service;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.domain.NotificationType;
import com.tripfit.tripfit.notification.event.ScheduleReminderEvent;
import com.tripfit.tripfit.notification.repository.NotificationHistoryRepository;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.event.AllMembersSubmittedEvent;
import com.tripfit.tripfit.trip.event.TripConfirmCanceledEvent;
import com.tripfit.tripfit.trip.event.TripConfirmedEvent;
import com.tripfit.tripfit.trip.event.TripInfoChangedEvent;
import com.tripfit.tripfit.trip.event.TripJoinCompletedEvent;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 여행방·리마인드 이벤트를 받아 알림을 만든다. 알림 이력은 이벤트를 발행한 트랜잭션이 커밋되기 직전에 같은 트랜잭션으로 저장하고,
// FCM 발송은 커밋된 뒤 전용 작업자에게 넘긴다. 그래서 원래 작업과 알림 이력은 함께 저장되거나 함께 취소되고,
// FCM이 멈춰도 원래 트랜잭션이나 DB 커넥션이 그 응답을 기다리지 않는다.
@Component
public class NotificationEventListener {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserRepository userRepository;

  private final NotificationHistoryRepository notificationHistoryRepository;

  private final UserDeviceTokenRepository userDeviceTokenRepository;

  private final NotificationPushDispatcher notificationPushDispatcher;

  public NotificationEventListener(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserRepository userRepository,
      NotificationHistoryRepository notificationHistoryRepository,
      UserDeviceTokenRepository userDeviceTokenRepository,
      NotificationPushDispatcher notificationPushDispatcher) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userRepository = userRepository;
    this.notificationHistoryRepository = notificationHistoryRepository;
    this.userDeviceTokenRepository = userDeviceTokenRepository;
    this.notificationPushDispatcher = notificationPushDispatcher;
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onTripJoinCompleted(TripJoinCompletedEvent event) {
    Trip trip = requireTrip(event.tripId());
    User joinedMember =
        userRepository.findById(event.joinedMemberUserId()).orElse(null);
    if (joinedMember == null) {
      return;
    }
    String body =
        joinedMember.displayName() + "님이 여행방에 참여했어요! 참여 현황을 확인해보세요.";
    dispatch(
        List.of(trip.getOwner()),
        trip,
        NotificationType.JOIN_COMPLETED,
        "여행방 참여 알림",
        body,
        LandingType.TRAVEL_ROOM_DETAIL);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onAllMembersSubmitted(AllMembersSubmittedEvent event) {
    Trip trip = requireTrip(event.tripId());
    dispatch(
        List.of(trip.getOwner()),
        trip,
        NotificationType.ALL_MEMBERS_SUBMITTED,
        "일정 제출 완료 알림",
        "모든 참여자의 일정이 제출되었어요! 추천 일정을 받아보세요.",
        LandingType.TRAVEL_ROOM_DETAIL);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onTripInfoChanged(TripInfoChangedEvent event) {
    Trip trip = requireTrip(event.tripId());
    dispatch(
        membersExcludingOwner(trip),
        trip,
        NotificationType.TRIP_INFO_CHANGED,
        "여행 정보 변경 알림",
        "여행 정보가 변경되었어요. 변경된 내용을 확인해보세요.",
        LandingType.TRAVEL_ROOM_DETAIL);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onTripConfirmed(TripConfirmedEvent event) {
    Trip trip = requireTrip(event.tripId());
    dispatch(
        membersExcludingOwner(trip),
        trip,
        NotificationType.TRIP_CONFIRMED,
        "일정 확정 알림",
        "여행 일정이 확정되었어요! 확정된 일정을 확인해보세요.",
        LandingType.TRAVEL_ROOM_DETAIL);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onTripConfirmCanceled(TripConfirmCanceledEvent event) {
    Trip trip = requireTrip(event.tripId());
    dispatch(
        membersExcludingOwner(trip),
        trip,
        NotificationType.TRIP_CONFIRM_CANCELED,
        "일정 확정 취소 알림",
        "확정된 여행 일정이 취소되었어요. 다시 일정을 조율해보세요.",
        LandingType.TRAVEL_ROOM_DETAIL);
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onScheduleReminder(ScheduleReminderEvent event) {
    List<User> recipients = userRepository.findAllById(event.userIds());
    String body = event.month() + "월 일정을 업데이트해보세요. 더 정확한 여행 일정을 추천받을 수 있어요.";
    dispatch(
        recipients,
        null,
        NotificationType.SCHEDULE_REMINDER,
        "일정 업데이트 알림",
        body,
        LandingType.SCHEDULE_MANAGEMENT);
  }

  private Trip requireTrip(UUID tripId) {
    return tripRepository
        .findById(tripId)
        .orElseThrow(
            () -> new IllegalStateException("Trip not found for notification dispatch: " + tripId));
  }

  private List<User> membersExcludingOwner(Trip trip) {
    return tripMemberRepository.findByTripIdAndDeletedAtIsNull(trip.getId()).stream()
        .filter(member -> member.getRole() != TripMemberRole.OWNER)
        .map(TripMember::getUser)
        .toList();
  }

  private void dispatch(
      List<User> recipients,
      Trip trip,
      NotificationType type,
      String title,
      String body,
      LandingType landingType) {
    List<User> eligible = recipients.stream().filter(User::isNotificationEnabled).toList();
    if (eligible.isEmpty()) {
      return;
    }
    // 1. 이 트랜잭션에 쌓인 변경(여행방 UPDATE 등)을 먼저 DB로 보낸다. 그대로 두면 커밋 시점의 flush가 알림 이력
    // INSERT를 UPDATE보다 먼저 보내는데, INSERT는 외래키 때문에 여행방 행에 공유 잠금을 걸어서 같은 방에 동시에
    // 들어온 두 요청이 서로의 UPDATE를 기다리는 데드락이 된다. Repository를 거쳐야 버전 충돌이 Spring 예외로 바뀐다.
    notificationHistoryRepository.flush();

    // 2. 알림 이력을 저장하고 수신 기기 토큰을 모은다.
    LocalDateTime sentAt = LocalDateTime.now();
    List<NotificationHistory> histories =
        eligible.stream()
            .map(
                user -> new NotificationHistory(user, trip, type, title, body, landingType, sentAt))
            .toList();
    notificationHistoryRepository.saveAll(histories);

    Map<UUID, UUID> historyIdByUserId = new HashMap<>();
    for (NotificationHistory history : histories) {
      historyIdByUserId.put(history.getUser().getId(), history.getId());
    }
    List<UUID> userIds = eligible.stream().map(User::getId).toList();
    Map<String, UUID> historyIdByToken = new HashMap<>();
    for (UserDeviceTokenRepository.UserTokenView view : userDeviceTokenRepository
        .findUserIdAndTokenByUserIdIn(userIds)) {
      historyIdByToken.put(view.getToken(), historyIdByUserId.get(view.getUserId()));
    }
    if (historyIdByToken.isEmpty()) {
      return;
    }

    // 3. 커밋된 뒤에만 발송한다. 롤백되면 이 콜백은 실행되지 않는다.
    UUID tripId = trip != null ? trip.getId() : null;
    NotificationPush push =
        new NotificationPush(type, historyIdByToken, title, body, landingType, tripId);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            notificationPushDispatcher.submit(push);
          }
        });
  }
}
