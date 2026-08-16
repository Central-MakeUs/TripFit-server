package com.tripfit.tripfit.common.holiday.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class HolidayApiClientTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void parseHolidays_multipleItems_returnsAllHolidayDates() {
    JsonNode response = read("""
        {"response":{"body":{"items":{"item":[
          {"dateKind":"01","dateName":"삼일절","isHoliday":"Y","locdate":20260301,"seq":1},
          {"dateKind":"01","dateName":"대체공휴일","isHoliday":"Y","locdate":20260302,"seq":1}
        ]}}}}
        """);

    assertThat(HolidayApiClient.parseHolidays(response))
        .containsExactlyInAnyOrder(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2));
  }

  @Test
  void parseHolidays_singleItemAsObject_returnsThatDate() {
    // 결과가 1건이면 item이 배열이 아니라 단일 객체로 오는 응답 형태
    JsonNode response = read("""
        {"response":{"body":{"items":{"item":
          {"dateKind":"01","dateName":"삼일절","isHoliday":"Y","locdate":20260301,"seq":1}
        }}}}
        """);

    assertThat(HolidayApiClient.parseHolidays(response))
        .containsExactly(LocalDate.of(2026, 3, 1));
  }

  @Test
  void parseHolidays_nonHolidayItem_isExcluded() {
    JsonNode response = read("""
        {"response":{"body":{"items":{"item":[
          {"dateName":"삼일절","isHoliday":"Y","locdate":20260301},
          {"dateName":"기념일","isHoliday":"N","locdate":20260305}
        ]}}}}
        """);

    assertThat(HolidayApiClient.parseHolidays(response))
        .containsExactly(LocalDate.of(2026, 3, 1));
  }

  @Test
  void parseHolidays_emptyItemsOrNull_returnsEmptySet() {
    // totalCount=0이면 items가 빈 문자열로 오는 응답 형태
    assertThat(HolidayApiClient.parseHolidays(read("""
        {"response":{"body":{"items":"","totalCount":0}}}
        """))).isEmpty();
    assertThat(HolidayApiClient.parseHolidays(null)).isEmpty();
  }

  private JsonNode read(String json) {
    return jsonMapper.readTree(json);
  }
}
