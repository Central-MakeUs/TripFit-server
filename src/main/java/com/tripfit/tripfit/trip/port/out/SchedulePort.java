package com.tripfit.tripfit.trip.port.out;

import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// trip이 필요로 하는 "사용자 정기·개별 일정 조회"를 인터페이스로 감싼 포트(의존 역전).
//
// 이 인터페이스는 trip 패키지에 있지만, 구현체(ScheduleAvailabilityAdapter)는 user.schedule 패키지에 있다 —
// trip은 user.schedule의
// RegularScheduleRepository·PersonalScheduleRepository·ScheduleCalendarResolver를
// 더 이상 직접 import하지 않고, 이 인터페이스 하나만 알면 된다. 예전에는 trip/service/의 여러 클래스가
// 저 repository들을 각자 주입받아 직접 쿼리했는데(TripServiceSupport.resolveMergedSchedules,
// RecommendationEngine.loadContext 등), 그 구현들이 사실상 같은 로직이라 이 포트 하나로 합쳐졌다.
//
// 이 포트를 새 메서드로 확장할 때는 "trip이 실제로 필요로 하는 조회"만 추가할 것 — user.schedule 서비스의
// 메서드를 통째로 노출하는 범용 인터페이스로 키우지 않는다(그러면 포트를 두는 의미가 없어짐).
public interface SchedulePort {

  // 사용자별 "정기 일정 원본"을 배치 조회한다(userId로 그룹핑된 상태로 반환 — 호출부가 다시 그룹핑할 필요 없음).
  // 요일별로 펼쳐지거나 구글 일정과 병합되지 않은 raw 데이터라서, 요일 매칭·연차(휴가) 계산처럼 정기 일정
  // 자체의 필드(daysOfWeek, 시간대 등)가 필요한 소비자만 쓴다 — 지금은 RecommendationEngine뿐.
  // 단순히 "이 기간에 가능/불가능한지"만 필요하면 이 메서드 대신 resolveMergedSchedules를 쓸 것.
  Map<UUID, List<RegularSchedule>> findRegularSchedulesByUserIds(List<UUID> userIds);

  // 사용자별 "개별 일정 원본"을 기간으로 배치 조회한다(userId로 그룹핑). 병합 전 override 자체(어떤 슬롯을
  // 직접 지정했는지)가 필요한 소비자만 쓴다 — 연차 자동 전환 시뮬레이션이 "정기 근무 때문에 막힌 건지,
  // 개별 일정으로 이미 막아둔 건지" 구분할 때 사용(RecommendationEngine, #105).
  Map<UUID, List<PersonalSchedule>> findPersonalSchedulesByUserIds(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate);

  // 정기+개별 일정을 구글 바쁨(busy) 정보와 합쳐, 사용자별 "날짜별 최종 슬롯 상태(오전/오후/저녁)" 달력을 만든다.
  // 정기·개별·구글 신호가 셋 다 없는 날짜는 결과에서 빠진다(sparse — 날짜 range 전체가 항상 채워지진 않음).
  //
  // googleBusyByUser는 이 메서드가 직접 조회하지 않는다 — 호출부가 GoogleCalendarPort.findBusyDaysByUserIds(...)를
  // 먼저 호출해서 그 결과를 그대로 넘겨야 한다. 두 포트를 "먼저 구글 busy 조회 → 그 결과를 넘겨서 병합"
  // 순서로 같이 쓰는 게 이 메서드의 전제다(TripMemberQueryService·TripScheduleSnapshotService·
  // RecommendationEngine이 전부 이 순서로 호출한다).
  Map<UUID, List<CalendarDayResponse>> resolveMergedSchedules(
      List<UUID> userIds,
      LocalDate startDate,
      LocalDate endDate,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser);
}
