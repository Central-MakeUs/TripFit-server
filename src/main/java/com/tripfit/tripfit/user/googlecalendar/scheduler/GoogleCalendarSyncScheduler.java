package com.tripfit.tripfit.user.googlecalendar.scheduler;

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

  // TEMP(디버그): freeBusy 실패 원인 확정을 위해 30분 → 5분으로 임시 단축(JITTER_SLOT_COUNT=6과 곱하면 유저별
  // 실질 재시도 주기도 3시간 → 30분으로 같이 줄어듦). 원인 확정되면 30분(30 * 60 * 1000L)으로 원복할 것 —
  // docs/specs/google-calendar-oauth.md "폴링 30분" Must Have와 지금 값이 다름
  private static final long SYNC_INTERVAL_MS = 5 * 60 * 1000L;

  private static final long JITTER_SLEEP_MS = 100L;

  private final UserRepository userRepository;

  private final GoogleCalendarService googleCalendarService;

  public GoogleCalendarSyncScheduler(
      UserRepository userRepository,
      GoogleCalendarService googleCalendarService) {
    this.userRepository = userRepository;
    this.googleCalendarService = googleCalendarService;
  }

  // 연동 유저 freeBusy sync — 유저별 hash 지터 + 짧은 sleep으로 부하 분산 (주기는 SYNC_INTERVAL_MS 참고)
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
        log.warn("Google Calendar sync failed for user {}", user.getId(), exception);
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
