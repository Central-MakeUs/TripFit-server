package com.tripfit.tripfit.common.holiday.scheduler;

import com.tripfit.tripfit.common.holiday.RedisHolidayProvider;
import com.tripfit.tripfit.common.holiday.client.HolidayApiClient;
import com.tripfit.tripfit.common.holiday.config.HolidayProperties;
import java.time.LocalDate;
import java.time.Year;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HolidaySyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(HolidaySyncScheduler.class);

  private static final long SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L;

  private static final int YEARS_AHEAD = 2;

  private final HolidayApiClient holidayApiClient;

  private final RedisHolidayProvider redisHolidayProvider;

  private final HolidayProperties holidayProperties;

  public HolidaySyncScheduler(
      HolidayApiClient holidayApiClient,
      RedisHolidayProvider redisHolidayProvider,
      HolidayProperties holidayProperties) {
    this.holidayApiClient = holidayApiClient;
    this.redisHolidayProvider = redisHolidayProvider;
    this.holidayProperties = holidayProperties;
  }

  @Scheduled(initialDelay = 0, fixedRate = SYNC_INTERVAL_MS)
  public void syncUpcomingYears() {
    if (holidayProperties.getServiceKey().isBlank()) {
      log.info("공휴일 API 인증키가 없어 동기화를 건너뜀. 공휴일 없음으로 동작");
      return;
    }
    int currentYear = Year.now().getValue();
    for (int year = currentYear; year <= currentYear + YEARS_AHEAD; year++) {
      syncYear(year);
    }
  }

  private void syncYear(int year) {
    try {
      Set<LocalDate> holidays = holidayApiClient.findHolidays(year);
      redisHolidayProvider.replaceYear(year, holidays);
    } catch (Exception exception) {
      log.warn("공휴일 {}년 동기화 실패. 기존 캐시 유지", year, exception);
    }
  }
}
