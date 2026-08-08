package com.tripfit.tripfit.notification.service;

import com.tripfit.tripfit.notification.domain.LandingType;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.domain.NotificationType;
import com.tripfit.tripfit.notification.event.AllMembersSubmittedEvent;
import com.tripfit.tripfit.notification.event.ScheduleReminderEvent;
import com.tripfit.tripfit.notification.event.TripConfirmCanceledEvent;
import com.tripfit.tripfit.notification.event.TripConfirmedEvent;
import com.tripfit.tripfit.notification.event.TripInfoChangedEvent;
import com.tripfit.tripfit.notification.event.TripJoinCompletedEvent;
import com.tripfit.tripfit.notification.repository.NotificationHistoryRepository;
import com.tripfit.tripfit.notification.repository.UserDeviceTokenRepository;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 여행방·리마인드 이벤트를 트랜잭션 커밋 후 받아 알림 문구를 만들고 FCM 발송·이력 저장을 수행한다(D5)
@Component
public class NotificationEventListener {

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  private final UserRepository userRepository;

  private final NotificationHistoryRepository notificationHistoryRepository;

  private final UserDeviceTokenRepository userDeviceTokenRepository;

  private final FcmService fcmService;

  public NotificationEventListener(
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository,
      UserRepository userRepository,
      NotificationHistoryRepository notificationHistoryRepository,
      UserDeviceTokenRepository userDeviceTokenRepository,
      FcmService fcmService) {
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
    this.userRepository = userRepository;
    this.notificationHistoryRepository = notificationHistoryRepository;
    this.userDeviceTokenRepository = userDeviceTokenRepository;
    this.fcmService = fcmService;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onTripJoinCompleted(TripJoinCompletedEvent event) {
    Trip trip = requireTrip(event.tripId());
    User joinedMember =
        userRepository.findById(event.joinedMemberUserId()).orElse(null);
    if (joinedMember == null) {
      return;
    }
    String body =
        ScheduleService.displayName(joinedMember) + "님이 여행방에 참여했어요! 참여 현황을 확인해보세요.";
    dispatch(
        List.of(trip.getOwner()),
        trip,
        NotificationType.JOIN_COMPLETED,
        "여행방 참여 알림",
        body,
        LandingType.TRAVEL_ROOM_DETAIL);
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    return tripRepository.findById(tripId).orElseThrow();
  }

  // ONGOING 여부와 무관하게 활성 멤버 전원 중 방장을 제외한 참여자(D3)
  private List<User> membersExcludingOwner(Trip trip) {
    return tripMemberRepository.findByTripIdAndDeletedAtIsNull(trip.getId()).stream()
        .filter(member -> member.getRole() != TripMemberRole.OWNER)
        .map(TripMember::getUser)
        .toList();
  }

  // 알림 수신자에 notification_enabled 게이트(D10)를 적용해 이력 저장 + FCM 발송
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
    LocalDateTime sentAt = LocalDateTime.now();
    List<NotificationHistory> histories =
        eligible.stream()
            .map(
                user -> new NotificationHistory(user, trip, type, title, body, landingType, sentAt))
            .toList();
    notificationHistoryRepository.saveAll(histories);

    // 토큰마다 소유 유저의 알림 이력 id를 붙여야 FCM data에 id를 실을 수 있음(멀티기기·멀티유저 혼재)
    // Collectors.toMap은 value null을 허용하지 않아 HashMap을 직접 채움
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
    UUID tripId = trip != null ? trip.getId() : null;
    fcmService.sendMulticast(historyIdByToken, title, body, landingType, tripId);
  }
}
