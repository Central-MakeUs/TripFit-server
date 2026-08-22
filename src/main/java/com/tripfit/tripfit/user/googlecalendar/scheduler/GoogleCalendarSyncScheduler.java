package com.tripfit.tripfit.user.googlecalendar.scheduler;

import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarSyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(GoogleCalendarSyncScheduler.class);

  private static final int JITTER_SLOT_COUNT = 6;

  private static final long SYNC_INTERVAL_MS = 30 * 60 * 1000L;

  private static final long JITTER_SLEEP_MS = 100L;

  private final UserRepository userRepository;

  private final GoogleCalendarService googleCalendarService;

  public GoogleCalendarSyncScheduler(
      UserRepository userRepository,
      GoogleCalendarService googleCalendarService) {
    this.userRepository = userRepository;
    this.googleCalendarService = googleCalendarService;
  }

  // 30분마다 연동 유저 freeBusy sync — 유저별 hash 지터 + 짧은 sleep으로 부하 분산
  @Scheduled(fixedRate = SYNC_INTERVAL_MS)
  public void syncConnectedUsers() {
    List<User> users = userRepository.findByIsGoogleCalendarConnectedTrue();
    long cycle = System.currentTimeMillis() / SYNC_INTERVAL_MS;
    for (User user : users) {
      if (shouldSkipThisCycle(user.getId(), cycle)) {
        continue;
      }
      try {
        googleCalendarService.syncUser(user.getId());
      } catch (Exception exception) {
        // syncUser 내부에서 이미 대부분의 실패를 삼키므로, 여기까지 오는 건 requireUser 등 예상 밖 오류
        SocialIntegrationLog.warn(
            log,
            SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
                .withUserId(user.getId())
                .withTrigger("SCHEDULED"),
            "Google Calendar scheduled sync failed unexpectedly",
            exception);
      }
      sleepJitter();
    }
  }

  private boolean shouldSkipThisCycle(UUID userId, long cycle) {
    int slot = Math.floorMod(userId.hashCode(), JITTER_SLOT_COUNT);
    return Math.floorMod(cycle, JITTER_SLOT_COUNT) != slot;
  }

  private void sleepJitter() {
    try {
      Thread.sleep(JITTER_SLEEP_MS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
