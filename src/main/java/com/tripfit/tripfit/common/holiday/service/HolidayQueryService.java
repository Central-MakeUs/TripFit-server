package com.tripfit.tripfit.common.holiday.service;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.common.holiday.dto.HolidayListResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HolidayQueryService {

  private final HolidayProvider holidayProvider;

  // 지정된 날짜 구간(startDate ~ endDate)에 해당하는 공휴일 목록을 정렬하여 반환합니다.
  public HolidayListResponse getHolidays(LocalDate startDate, LocalDate endDate) {
    if (startDate.isAfter(endDate)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    List<LocalDate> holidays = holidayProvider.findHolidaysBetween(startDate, endDate).stream()
        .sorted()
        .toList();
    return new HolidayListResponse(startDate, endDate, holidays);
  }
}
