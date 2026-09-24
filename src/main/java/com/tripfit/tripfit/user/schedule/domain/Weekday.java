package com.tripfit.tripfit.user.schedule.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Schema(description = "정기 일정이 반복되는 요일입니다.")
public enum Weekday {
  @Schema(description = "월요일을 나타냅니다.")
  MON(DayOfWeek.MONDAY),

  @Schema(description = "화요일을 나타냅니다.")
  TUE(DayOfWeek.TUESDAY),

  @Schema(description = "수요일을 나타냅니다.")
  WED(DayOfWeek.WEDNESDAY),

  @Schema(description = "목요일을 나타냅니다.")
  THU(DayOfWeek.THURSDAY),

  @Schema(description = "금요일을 나타냅니다.")
  FRI(DayOfWeek.FRIDAY),

  @Schema(description = "토요일을 나타냅니다.")
  SAT(DayOfWeek.SATURDAY),

  @Schema(description = "일요일을 나타냅니다.")
  SUN(DayOfWeek.SUNDAY);

  private final DayOfWeek dayOfWeek;

  Weekday(DayOfWeek dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public DayOfWeek toDayOfWeek() {
    return dayOfWeek;
  }

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
