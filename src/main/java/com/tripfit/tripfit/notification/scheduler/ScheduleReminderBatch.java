package com.tripfit.tripfit.notification.scheduler;

import com.tripfit.tripfit.notification.event.ScheduleReminderEvent;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ScheduleReminderBatch {

  private static final Logger log = LoggerFactory.getLogger(ScheduleReminderBatch.class);

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private static final int BATCH_SIZE = 500;

  private final UserRepository userRepository;

  private final ApplicationEventPublisher applicationEventPublisher;

  private final TransactionTemplate transactionTemplate;

  public ScheduleReminderBatch(
      UserRepository userRepository,
      ApplicationEventPublisher applicationEventPublisher,
      TransactionTemplate transactionTemplate) {
    this.userRepository = userRepository;
    this.applicationEventPublisher = applicationEventPublisher;
    this.transactionTemplate = transactionTemplate;
  }

  // 매월 1일·15일 오전 9시에 알림을 켠 사용자 전체에게 일정 업데이트 리마인드를 보낸다.
  // 알림 이력은 이벤트를 발행한 트랜잭션 안에서 저장되므로, 500명마다 쓰기 트랜잭션을 따로 연다.
  // 전체를 한 트랜잭션에 넣으면 수신자 사용자 행의 잠금을 오래 쥐어 그동안 로그인·프로필 수정이 기다린다.
  // 한 묶음이 실패해도(일시적인 DB 오류 등) 그 묶음만 롤백하고 나머지 묶음은 계속 보낸다.
  @Scheduled(cron = "0 0 9 1,15 * *", zone = "Asia/Seoul")
  public void run() {
    int month = LocalDate.now(KST).getMonthValue();
    List<UUID> userIds = userRepository.findIdsForScheduleReminder();
    for (int i = 0; i < userIds.size(); i += BATCH_SIZE) {
      List<UUID> batch = userIds.subList(i, Math.min(i + BATCH_SIZE, userIds.size()));
      try {
        transactionTemplate.executeWithoutResult(
            status -> applicationEventPublisher
                .publishEvent(new ScheduleReminderEvent(batch, month)));
      } catch (RuntimeException exception) {
        log.error(
            "Schedule reminder batch failed. skipping this batch. offset={}, size={}",
            i,
            batch.size(),
            exception);
      }
    }
  }
}
