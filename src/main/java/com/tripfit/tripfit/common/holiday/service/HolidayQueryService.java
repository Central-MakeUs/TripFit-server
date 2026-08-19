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

  // 캘린더 화면의 공휴일 빨간 글씨 표시용 — 판정 로직 없이 날짜만 오름차순으로 돌려준다
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
