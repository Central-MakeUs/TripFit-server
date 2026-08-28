package com.tripfit.tripfit.common.holiday.client;

import com.tripfit.tripfit.common.holiday.config.HolidayProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class HolidayApiClient {

  private static final String REST_DAY_URL =
      "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";

  private static final int NUM_OF_ROWS = 100;

  private static final DateTimeFormatter LOCDATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final RestClient restClient;

  private final HolidayProperties holidayProperties;

  public HolidayApiClient(RestClient restClient, HolidayProperties holidayProperties) {
    this.restClient = restClient;
    this.holidayProperties = holidayProperties;
  }

  public Set<LocalDate> findHolidays(int year) {
    JsonNode response =
        restClient
            .get()
            .uri(
                REST_DAY_URL + "?serviceKey={serviceKey}&solYear={solYear}&numOfRows={numOfRows}"
                    + "&_type=json",
                holidayProperties.getServiceKey(),
                year,
                NUM_OF_ROWS)
            .retrieve()
            .body(JsonNode.class);
    return parseHolidays(response);
  }

  static Set<LocalDate> parseHolidays(JsonNode response) {
    Set<LocalDate> holidays = new HashSet<>();
    if (response == null) {
      return holidays;
    }
    JsonNode item = response.path("response").path("body").path("items").path("item");
    if (item.isObject()) {
      addIfHoliday(holidays, item);
      return holidays;
    }
    if (item.isArray()) {
      for (JsonNode element : item) {
        addIfHoliday(holidays, element);
      }
    }
    return holidays;
  }

  private static void addIfHoliday(Set<LocalDate> holidays, JsonNode item) {
    if (!"Y".equalsIgnoreCase(item.path("isHoliday").asText(""))) {
      return;
    }
    String locdate = item.path("locdate").asText("");
    if (locdate.length() == 8) {
      holidays.add(LocalDate.parse(locdate, LOCDATE));
    }
  }
}
