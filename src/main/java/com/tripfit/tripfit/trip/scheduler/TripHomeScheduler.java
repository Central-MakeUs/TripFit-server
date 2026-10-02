package com.tripfit.tripfit.trip.scheduler;

import com.tripfit.tripfit.common.retry.VersionConflictRetryExecutor;
import com.tripfit.tripfit.trip.service.TripHomeMaintenanceService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TripHomeScheduler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final TripHomeMaintenanceService tripHomeMaintenanceService;

  private final VersionConflictRetryExecutor versionConflictRetryExecutor;

  public TripHomeScheduler(
      TripHomeMaintenanceService tripHomeMaintenanceService,
      VersionConflictRetryExecutor versionConflictRetryExecutor) {
    this.tripHomeMaintenanceService = tripHomeMaintenanceService;
    this.versionConflictRetryExecutor = versionConflictRetryExecutor;
  }

  @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
  public void runDailyMaintenance() {
    // 만료된 여행방 전체를 한 트랜잭션에서 고치므로, 그중 한 방이라도 다른 요청과 부딪히면 전체가 취소된다.
    // 하루에 한 번만 도는 작업이라 그대로 두면 다음 날까지 밀리기 때문에 다시 시도한다.
    versionConflictRetryExecutor.run(
        () -> tripHomeMaintenanceService.runForDate(LocalDate.now(KST)));
  }
}
