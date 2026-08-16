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

// 공휴일 캐시 갱신 — 하루 1회 + 기동 직후 1회.
//
// 공휴일은 정부가 몇 년 치를 미리 확정 발표하므로 자주 바꿀 이유가 없다. 하루 1회로 잡은 건 연중에 갑자기
// 지정되는 임시공휴일을 늦어도 다음 날에는 반영하기 위해서다(구글 캘린더 sync가 30분 주기인 것과 목적이 다름).
@Component
public class HolidaySyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(HolidaySyncScheduler.class);

  private static final long SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L;

  // 마이페이지 달력 조회 윈도우가 today~+2년이라, 올해·내년·내후년 3개년이면 항상 커버된다
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

  // 연도별로 따로 호출·저장해, 한 해가 실패해도 나머지 해의 캐시는 갱신되게 함
  @Scheduled(initialDelay = 0, fixedRate = SYNC_INTERVAL_MS)
  public void syncUpcomingYears() {
    if (holidayProperties.getServiceKey().isBlank()) {
      log.info("공휴일 API 인증키가 없어 동기화를 건너뜀 — 공휴일 없음으로 동작");
      return;
    }
    int currentYear = Year.now().getValue();
    for (int year = currentYear; year <= currentYear + YEARS_AHEAD; year++) {
      syncYear(year);
    }
  }

  // 호출 실패 시 그날 갱신만 포기하고 기존 캐시를 그대로 둔다 — 공휴일은 거의 바뀌지 않아 어제 값으로 충분함
  private void syncYear(int year) {
    try {
      Set<LocalDate> holidays = holidayApiClient.findHolidays(year);
      redisHolidayProvider.replaceYear(year, holidays);
    } catch (Exception exception) {
      log.warn("공휴일 {}년 동기화 실패 — 기존 캐시 유지", year, exception);
    }
  }
}
