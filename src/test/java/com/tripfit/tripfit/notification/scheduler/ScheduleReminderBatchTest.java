package com.tripfit.tripfit.notification.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.notification.event.ScheduleReminderEvent;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ScheduleReminderBatchTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher applicationEventPublisher;

  // 실제 트랜잭션 없이 콜백만 바로 실행해, 배치마다 트랜잭션이 따로 열리는지 횟수로 확인한다.
  private final CountingTransactionManager transactionManager = new CountingTransactionManager();

  private final TransactionTemplate transactionTemplate =
      new TransactionTemplate(transactionManager);

  @Test
  void run_splitsRecipientsInto500Batches() {
    List<UUID> userIds =
        Stream.generate(UUID::randomUUID).limit(1200).collect(java.util.stream.Collectors.toList());
    when(userRepository.findIdsForScheduleReminder()).thenReturn(userIds);
    ScheduleReminderBatch batch =
        new ScheduleReminderBatch(userRepository, applicationEventPublisher, transactionTemplate);

    batch.run();

    ArgumentCaptor<ScheduleReminderEvent> captor =
        ArgumentCaptor.forClass(ScheduleReminderEvent.class);
    verify(applicationEventPublisher, times(3)).publishEvent(captor.capture());
    List<ScheduleReminderEvent> events = captor.getAllValues();
    int total = events.stream().mapToInt(event -> event.userIds().size()).sum();
    org.assertj.core.api.Assertions.assertThat(total).isEqualTo(1200);
    org.assertj.core.api.Assertions.assertThat(events).allSatisfy(
        event -> org.assertj.core.api.Assertions.assertThat(event.userIds().size())
            .isLessThanOrEqualTo(500));
    org.assertj.core.api.Assertions.assertThat(transactionManager.begun).isEqualTo(3);
  }

  // 한 묶음이 실패해도 그 묶음만 건너뛰고 나머지 묶음은 계속 보낸다.
  @Test
  void run_whenOneBatchFails_continuesWithRemainingBatches() {
    List<UUID> userIds =
        Stream.generate(UUID::randomUUID).limit(1200).collect(java.util.stream.Collectors.toList());
    when(userRepository.findIdsForScheduleReminder()).thenReturn(userIds);
    org.mockito.Mockito.doNothing()
        .doThrow(new IllegalStateException("temporary failure"))
        .doNothing()
        .when(applicationEventPublisher)
        .publishEvent(any(ScheduleReminderEvent.class));
    ScheduleReminderBatch batch =
        new ScheduleReminderBatch(userRepository, applicationEventPublisher, transactionTemplate);

    batch.run();

    verify(applicationEventPublisher, times(3)).publishEvent(any(ScheduleReminderEvent.class));
  }

  @Test
  void run_noEligibleUsers_publishesNothing() {
    when(userRepository.findIdsForScheduleReminder()).thenReturn(new ArrayList<>());
    ScheduleReminderBatch batch =
        new ScheduleReminderBatch(userRepository, applicationEventPublisher, transactionTemplate);

    batch.run();

    verify(applicationEventPublisher, times(0)).publishEvent(any());
  }

  private static final class CountingTransactionManager extends AbstractPlatformTransactionManager {

    private int begun;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun++;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
