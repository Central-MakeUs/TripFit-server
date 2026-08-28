package com.tripfit.tripfit.common.holiday;

import java.time.LocalDate;
import java.util.Set;

public interface HolidayProvider {

  Set<LocalDate> findHolidaysBetween(LocalDate startDate, LocalDate endDate);
}
