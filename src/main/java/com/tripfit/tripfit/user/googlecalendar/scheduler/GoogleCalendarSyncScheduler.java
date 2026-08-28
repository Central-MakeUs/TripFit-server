package com.tripfit.tripfit.user.googlecalendar.scheduler;

import com.tripfit.tripfit.common.logging.SocialIntegrationAction;
import com.tripfit.tripfit.common.logging.SocialIntegrationLog;
import com.tripfit.tripfit.common.logging.SocialLogContext;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarSyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(GoogleCalendarSyncScheduler.class);

  private static final long SYNC_INTERVAL_MS = 30 * 60 * 1000L;

  private static final long CALL_SPACING_MS = 100L;

  private final UserRepository userRepository;

  private final GoogleCalendarService googleCalendarService;

  public GoogleCalendarSyncScheduler(
      UserRepository userRepository,
      GoogleCalendarService googleCalendarService) {
    this.userRepository = userRepository;
    this.googleCalendarService = googleCalendarService;
  }

  @Scheduled(fixedRate = SYNC_INTERVAL_MS)
  public void syncConnectedUsers() {
    List<User> users = userRepository.findByIsGoogleCalendarConnectedTrue();
    for (User user : users) {
      try {
        googleCalendarService.syncUser(user.getId());
      } catch (Exception exception) {

        SocialIntegrationLog.warn(
            log,
            SocialLogContext.of(SocialProvider.GOOGLE, SocialIntegrationAction.CALENDAR_SYNC)
                .withUserId(user.getId())
                .withTrigger("SCHEDULED"),
            "Google Calendar scheduled sync failed unexpectedly",
            exception);
      }
      sleepBetweenUsers();
    }
  }

  private void sleepBetweenUsers() {
    try {
      Thread.sleep(CALL_SPACING_MS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
