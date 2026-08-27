package com.tripfit.tripfit.user.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WeekdayTest {

  @Test
  void normalizeCsv_upperTrimDedup() {
    assertThat(Weekday.normalizeCsv(" mon, tue ,MON "))
        .isEqualTo("MON,TUE");
  }

  @Test
  void normalizeCsv_nullOrBlank_returnsNull() {
    assertThat(Weekday.normalizeCsv(null)).isNull();
    assertThat(Weekday.normalizeCsv("  ")).isNull();
  }

  @Test
  void normalizeCsv_invalidToken_throws() {
    assertThatThrownBy(() -> Weekday.normalizeCsv("MON,FOO"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
