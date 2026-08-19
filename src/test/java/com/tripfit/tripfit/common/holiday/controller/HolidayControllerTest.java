package com.tripfit.tripfit.common.holiday.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.GlobalExceptionHandler;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.holiday.dto.HolidayListResponse;
import com.tripfit.tripfit.common.holiday.service.HolidayQueryService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class HolidayControllerTest {

  @Mock
  private HolidayQueryService holidayQueryService;

  private MockMvc mockMvc() {
    return MockMvcBuilders.standaloneSetup(new HolidayController(holidayQueryService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  void getHolidays_returnsHolidayList() throws Exception {
    LocalDate start = LocalDate.of(2027, 1, 1);
    LocalDate end = LocalDate.of(2027, 1, 31);
    when(holidayQueryService.getHolidays(eq(start), eq(end)))
        .thenReturn(new HolidayListResponse(start, end, List.of(LocalDate.of(2027, 1, 1))));

    mockMvc()
        .perform(
            get("/api/v1/holidays").param("startDate", "2027-01-01").param(
                "endDate",
                "2027-01-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.startDate").value("2027-01-01"))
        .andExpect(jsonPath("$.data.holidays[0]").value("2027-01-01"));
  }

  @Test
  void getHolidays_startAfterEnd_returns400InvalidInput() throws Exception {
    LocalDate start = LocalDate.of(2027, 2, 1);
    LocalDate end = LocalDate.of(2027, 1, 1);
    doThrow(new TripFitException(CommonErrorCode.INVALID_INPUT))
        .when(holidayQueryService)
        .getHolidays(eq(start), eq(end));

    mockMvc()
        .perform(
            get("/api/v1/holidays").param("startDate", "2027-02-01").param(
                "endDate",
                "2027-01-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void getHolidays_missingStartDate_returns400InvalidInput() throws Exception {
    mockMvc()
        .perform(get("/api/v1/holidays").param("endDate", "2027-01-31"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }
}
