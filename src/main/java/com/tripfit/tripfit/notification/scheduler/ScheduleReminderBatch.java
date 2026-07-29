package com.tripfit.tripfit.notification.scheduler;

import com.tripfit.tripfit.notification.event.ScheduleReminderEvent;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// BR-NOTI-005 — 매월 1·15일 09:00(KST) notification_enabled=true 사용자에게 리마인드 발행(D6)
@Component
public class ScheduleReminderBatch {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private static final int BATCH_SIZE = 500;

  private final UserRepository userRepository;

  private final ApplicationEventPublisher applicationEventPublisher;

  public ScheduleReminderBatch(
      UserRepository userRepository, ApplicationEventPublisher applicationEventPublisher) {
    this.userRepository = userRepository;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Scheduled(cron = "0 0 9 1,15 * *", zone = "Asia/Seoul")
  @Transactional(readOnly = true)
  public void run() {
    int month = LocalDate.now(KST).getMonthValue();
    List<UUID> userIds = userRepository.findIdsForScheduleReminder();
    for (int i = 0; i < userIds.size(); i += BATCH_SIZE) {
      List<UUID> batch = userIds.subList(i, Math.min(i + BATCH_SIZE, userIds.size()));
      applicationEventPublisher.publishEvent(new ScheduleReminderEvent(batch, month));
    }
  }
}
