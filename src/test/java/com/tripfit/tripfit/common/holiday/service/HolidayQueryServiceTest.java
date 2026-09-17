package com.tripfit.tripfit.common.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.common.holiday.dto.HolidayListResponse;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HolidayQueryServiceTest {

  @Mock
  private HolidayProvider holidayProvider;

  private HolidayQueryService service() {
    return new HolidayQueryService(holidayProvider);
  }

  @Test
  void getHolidays_returnsSortedDatesWithEchoedRange() {
    LocalDate start = LocalDate.of(2027, 1, 1);
    LocalDate end = LocalDate.of(2027, 1, 31);
    when(holidayProvider.findHolidaysBetween(start, end))
        .thenReturn(Set.of(LocalDate.of(2027, 1, 1)));

    HolidayListResponse response = service().getHolidays(start, end);

    assertThat(response.startDate()).isEqualTo(start);
    assertThat(response.endDate()).isEqualTo(end);
    assertThat(response.holidays()).containsExactly(LocalDate.of(2027, 1, 1));
  }

  @Test
  void getHolidays_noHolidaysInRange_returnsEmptyListNotError() {
    when(holidayProvider.findHolidaysBetween(any(), any())).thenReturn(Set.of());

    HolidayListResponse response =
        service().getHolidays(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28));

    assertThat(response.holidays()).isEmpty();
  }

  @Test
  void getHolidays_multipleHolidays_sortedAscending() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    LocalDate end = LocalDate.of(2026, 12, 31);
    when(holidayProvider.findHolidaysBetween(start, end))
        .thenReturn(
            Set.of(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 5)));

    HolidayListResponse response = service().getHolidays(start, end);

    assertThat(response.holidays())
        .containsExactly(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 10, 9));
  }

  @Test
  void getHolidays_startAfterEnd_throwsInvalidInput() {
    assertThatThrownBy(
        () -> service().getHolidays(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 1, 1)))
        .isInstanceOf(TripFitException.class);
  }
}
