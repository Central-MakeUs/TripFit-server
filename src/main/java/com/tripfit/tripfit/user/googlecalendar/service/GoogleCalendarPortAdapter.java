package com.tripfit.tripfit.user.googlecalendar.service;

import com.tripfit.tripfit.trip.port.out.GoogleCalendarPort;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

// trip.port.out.GoogleCalendarPort 구현체 — 기존 GoogleCalendarService에 그대로 위임한다.
//
// 이 클래스는 로직이 없다(순수 위임/포워딩) — GoogleCalendarService가 이미 배치 조회 메서드를 갖고 있어서
// 그대로 감싸기만 하면 됐다. trip 쪽에서 GoogleCalendarService를 직접 주입받지 않고 이 어댑터(포트 타입)를
// 통해서만 접근하게 하는 게 이 클래스의 유일한 목적 — 나중에 구글 캘린더 조회 방식이 바뀌어도(예: 캐싱 추가,
// 배치 쿼리 최적화) trip 쪽 코드는 전혀 손댈 필요가 없다.
@Component
public class GoogleCalendarPortAdapter implements GoogleCalendarPort {

  private final GoogleCalendarService googleCalendarService;

  public GoogleCalendarPortAdapter(GoogleCalendarService googleCalendarService) {
    this.googleCalendarService = googleCalendarService;
  }

  @Override
  public Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> findBusyDaysByUserIds(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate) {
    return googleCalendarService.findBusyDaysByUserIds(userIds, startDate, endDate);
  }
}
