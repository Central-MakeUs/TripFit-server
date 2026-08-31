package com.tripfit.tripfit.trip.port.out;

import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// trip이 필요로 하는 "구글 캘린더 연동 사용자의 바쁨(busy) 정보 조회"를 인터페이스로 감싼 포트(의존 역전).
//
// 이 인터페이스는 trip 패키지에 있지만, 구현체(GoogleCalendarPortAdapter)는 user.googlecalendar 패키지에 있다 —
// trip은 GoogleCalendarService를 더 이상 직접 import하지 않고 이 인터페이스만 안다. 실제 조회·동기화 로직은
// 전부 GoogleCalendarService에 그대로 남아 있고, 이 포트는 그 중 trip이 쓰는 조회 메서드 하나만 노출한다.
public interface GoogleCalendarPort {

  // userId·날짜별 구글 busy 정보를 배치 조회한다. 구글 캘린더를 연동하지 않았거나 해당 기간에 busy 일정이
  // 없는 사용자는 결과 Map에서 그냥 빠진다(예외를 던지지 않음) — 호출부는
  // googleBusyByUser.getOrDefault(userId, Map.of())처럼 없는 값을 "바쁜 일정 없음"으로 취급해야 한다.
  // 이 메서드의 결과는 그대로 SchedulePort.resolveMergedSchedules(...)의 googleBusyByUser 인자로 넘겨서 쓴다.
  Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> findBusyDaysByUserIds(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate);
}
