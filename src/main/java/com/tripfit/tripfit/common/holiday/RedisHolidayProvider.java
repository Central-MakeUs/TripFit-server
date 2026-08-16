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

// 공휴일 캐시(Redis) — 연도별 키 하나에 그 해 공휴일 날짜를 Set으로 담는다. 조회 실패는 전부 fail-open(빈 집합)
// 이라, Redis 장애가 달력·추천 API를 막지 않고 "공휴일을 모르는 상태"로만 degrade된다(decisions/010·011).
//
// TTL을 두는 이유: 동기화 스케줄러가 며칠 연속 실패하면 캐시가 자연 만료돼, 오래된 공휴일 목록을 계속 믿는
// 대신 fail-open으로 넘어간다(사람이 손대지 않아도 복구되는 구조). 정상 운영 중에는 매일 갱신이 TTL을 밀어낸다.
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
    // 구간이 걸친 연도의 키를 전부 읽어 합친 뒤 요청 구간으로 잘라낸다 — 조회 윈도우가 해를 넘길 수 있음
    for (int year = startDate.getYear(); year <= endDate.getYear(); year++) {
      for (LocalDate holiday : readYear(year)) {
        if (!holiday.isBefore(startDate) && !holiday.isAfter(endDate)) {
          holidays.add(holiday);
        }
      }
    }
    return holidays;
  }

  // 한 해치를 통째로 교체 — 임시 키에 다 쓴 뒤 RENAME으로 원자 교체해, 갱신 중에 조회가 빈 집합을 보지 않게 함
  // 빈 목록은 쓰지 않는다: 정상적인 해라면 공휴일이 0개일 수 없으므로 API 응답 이상으로 보고 기존 캐시를 지킨다
  public void replaceYear(int year, Set<LocalDate> holidays) {
    if (holidays.isEmpty()) {
      log.warn("공휴일 {}년 응답이 비어 있어 캐시를 갱신하지 않음 — 기존 값 유지", year);
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
      log.warn("공휴일 {}년 캐시 갱신 실패 — 기존 값 유지", year, exception);
      discardQuietly(stagingKey);
    }
  }

  private Set<LocalDate> readYear(int year) {
    Set<LocalDate> holidays = new HashSet<>();
    Set<String> members;
    try {
      members = redisTemplate.opsForSet().members(keyOf(year));
    } catch (DataAccessException exception) {
      log.warn("공휴일 캐시 조회 실패 — 공휴일 없음으로 간주(fail-open)", exception);
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
      // 임시 키는 TTL로도 사라지므로 정리 실패는 무시
    }
  }

  private static String keyOf(int year) {
    return KEY_PREFIX + year;
  }
}
