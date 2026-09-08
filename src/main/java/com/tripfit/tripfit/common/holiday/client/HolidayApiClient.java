package com.tripfit.tripfit.common.holiday.client;

import com.tripfit.tripfit.common.holiday.config.HolidayProperties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

// 공공데이터포털 한국천문연구원 특일 정보 — 관공서 공휴일(getRestDeInfo) 조회.
//
// 이 오퍼레이션은 대체공휴일·임시공휴일까지 포함해 "그날 관공서가 쉬는지"를 돌려주므로, 정기 일정의
// 공휴일 휴무 판정에 그대로 쓸 수 있다(국경일만 주는 getHoliDeInfo와 다름).
@Component
public class HolidayApiClient {

  private static final String REST_DAY_URL =
      "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo";

  // 한 해 공휴일은 20일 내외라 한 페이지로 충분 — 페이지네이션을 돌 필요가 없다
  private static final int NUM_OF_ROWS = 100;

  private static final DateTimeFormatter LOCDATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final RestClient restClient;

  private final HolidayProperties holidayProperties;

  public HolidayApiClient(RestClient restClient, HolidayProperties holidayProperties) {
    this.restClient = restClient;
    this.holidayProperties = holidayProperties;
  }

  // 해당 연도의 공휴일 전체를 조회한다 — 호출 실패는 예외로 전파되므로 스케줄러가 잡아 그날 갱신만 건너뛴다
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

  // 응답에서 실제 휴무일(locdate)만 추려낸다 — totalCount=0이면 items가 빈 문자열로, 1건이면 item이 배열이
  // 아닌 단일 객체로 오는 등 형태가 흔들려서 노드 타입을 매번 확인한다
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

  // isHoliday가 "Y"인 항목만 채택 — 공휴일이 아닌 기념일이 섞여 오더라도 근무일 판정에 영향을 주지 않게 함
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
