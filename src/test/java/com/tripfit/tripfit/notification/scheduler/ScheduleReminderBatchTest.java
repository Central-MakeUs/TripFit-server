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

@ExtendWith(MockitoExtension.class)
class ScheduleReminderBatchTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher applicationEventPublisher;

  @Test
  void run_splitsRecipientsInto500Batches() {
    List<UUID> userIds =
        Stream.generate(UUID::randomUUID).limit(1200).collect(java.util.stream.Collectors.toList());
    when(userRepository.findIdsForScheduleReminder()).thenReturn(userIds);
    ScheduleReminderBatch batch =
        new ScheduleReminderBatch(userRepository, applicationEventPublisher);

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
  }

  @Test
  void run_noEligibleUsers_publishesNothing() {
    when(userRepository.findIdsForScheduleReminder()).thenReturn(new ArrayList<>());
    ScheduleReminderBatch batch =
        new ScheduleReminderBatch(userRepository, applicationEventPublisher);

    batch.run();

    verify(applicationEventPublisher, times(0)).publishEvent(any());
  }
}
