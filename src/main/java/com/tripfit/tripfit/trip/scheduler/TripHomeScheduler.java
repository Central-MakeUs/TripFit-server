package com.tripfit.tripfit.trip.scheduler;

import com.tripfit.tripfit.trip.service.TripHomeMaintenanceService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TripHomeScheduler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final TripHomeMaintenanceService tripHomeMaintenanceService;

  public TripHomeScheduler(TripHomeMaintenanceService tripHomeMaintenanceService) {
    this.tripHomeMaintenanceService = tripHomeMaintenanceService;
  }

  @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
  public void runDailyMaintenance() {
    tripHomeMaintenanceService.runForDate(LocalDate.now(KST));
  }
}
