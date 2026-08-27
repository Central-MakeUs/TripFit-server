package com.tripfit.tripfit.user.schedule.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Schema(description = "정기 일정 반복 요일. API·DB는 콤마 구분 문자열(MON,TUE,...)")
public enum Weekday {
  @Schema(description = "월요일")
  MON(DayOfWeek.MONDAY),

  @Schema(description = "화요일")
  TUE(DayOfWeek.TUESDAY),

  @Schema(description = "수요일")
  WED(DayOfWeek.WEDNESDAY),

  @Schema(description = "목요일")
  THU(DayOfWeek.THURSDAY),

  @Schema(description = "금요일")
  FRI(DayOfWeek.FRIDAY),

  @Schema(description = "토요일")
  SAT(DayOfWeek.SATURDAY),

  @Schema(description = "일요일")
  SUN(DayOfWeek.SUNDAY);

  private final DayOfWeek dayOfWeek;

  Weekday(DayOfWeek dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public DayOfWeek toDayOfWeek() {
    return dayOfWeek;
  }

  // 토큰(MON·MONDAY 등)을 Weekday로 변환. 알 수 없으면 null
  public static Weekday fromToken(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    String normalized = token.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "MON", "MONDAY" -> MON;
      case "TUE", "TUESDAY" -> TUE;
      case "WED", "WEDNESDAY" -> WED;
      case "THU", "THURSDAY" -> THU;
      case "FRI", "FRIDAY" -> FRI;
      case "SAT", "SATURDAY" -> SAT;
      case "SUN", "SUNDAY" -> SUN;
      default -> null;
    };
  }

  // CSV를 검증·정규화(대문자·trim·중복 제거·등장 순). null/blank → null. 잘못된 토큰 → IllegalArgumentException
  public static String normalizeCsv(String daysOfWeekCsv) {
    if (daysOfWeekCsv == null || daysOfWeekCsv.isBlank()) {
      return null;
    }
    Set<Weekday> unique = new LinkedHashSet<>();
    for (String token : daysOfWeekCsv.split(",")) {
      String trimmed = token.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      Weekday weekday = fromToken(trimmed);
      if (weekday == null) {
        throw new IllegalArgumentException("invalid weekday: " + trimmed);
      }
      unique.add(weekday);
    }
    if (unique.isEmpty()) {
      return null;
    }
    List<String> names = new ArrayList<>(unique.size());
    for (Weekday weekday : unique) {
      names.add(weekday.name());
    }
    return String.join(",", names);
  }
}
