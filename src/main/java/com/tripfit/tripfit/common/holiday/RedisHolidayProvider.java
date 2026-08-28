package com.tripfit.tripfit.common.holiday;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisHolidayProvider implements HolidayProvider {

  private static final Logger log = LoggerFactory.getLogger(RedisHolidayProvider.class);

  private static final String KEY_PREFIX = "holiday:kr:";

  private static final Duration TTL = Duration.ofDays(7);

  private static final DateTimeFormatter LOCDATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final StringRedisTemplate redisTemplate;

  public RedisHolidayProvider(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Set<LocalDate> findHolidaysBetween(LocalDate startDate, LocalDate endDate) {
    Set<LocalDate> holidays = new HashSet<>();
    if (startDate.isAfter(endDate)) {
      return holidays;
    }

    for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
      for (LocalDate holiday : readYear(year)) {
        if (!holiday.isBefore(startDate) && !holiday.isAfter(endDate)) {
          holidays.add(holiday);
        }
      }
    }
    return holidays;
  }

  public void replaceYear(int year, Set<LocalDate> holidays) {
    if (holidays.isEmpty()) {
      log.warn("공휴일 {}년 응답이 비어 있어 캐시를 갱신하지 않음. 기존 값 유지", year);
      return;
    }
    String key = keyOf(year);
    String stagingKey = key + ":staging:" + UUID.randomUUID();
    try {
      String[] members = holidays.stream().map(LOCDATE::format).toArray(String[]::new);
      redisTemplate.opsForSet().add(stagingKey, members);
      redisTemplate.expire(stagingKey, TTL);
      redisTemplate.rename(stagingKey, key);
    } catch (DataAccessException exception) {
      log.warn("공휴일 {}년 캐시 갱신 실패. 기존 값 유지", year, exception);
      discardQuietly(stagingKey);
    }
  }

  private Set<LocalDate> readYear(int year) {
    Set<LocalDate> holidays = new HashSet<>();
    Set<String> members;
    try {
      members = redisTemplate.opsForSet().members(keyOf(year));
    } catch (DataAccessException exception) {
      log.warn("공휴일 캐시 조회 실패. 공휴일 없음으로 간주(fail-open)", exception);
      return holidays;
    }
    if (members == null) {
      return holidays;
    }
    for (String member : members) {
      holidays.add(LocalDate.parse(member, LOCDATE));
    }
    return holidays;
  }

  private void discardQuietly(String stagingKey) {
    try {
      redisTemplate.delete(stagingKey);
    } catch (DataAccessException ignored) {

    }
  }

  private static String keyOf(int year) {
    return KEY_PREFIX + year;
  }
}
