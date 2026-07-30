package com.tripfit.tripfit.trip.service;

import com.tripfit.tripfit.trip.domain.AttendanceType;
import com.tripfit.tripfit.trip.domain.RecommendationMode;
import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.SlotStatuses;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleCalendarResolver;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 후보 윈도우 생성 → 참여자 3분류 → 평가항목 4종 패널티·모드 가중치 스코어링 → 동점 처리 → Best3 산출(#50 BR-TRIP-005·011·012)
@Component
class RecommendationEngine {

  private static final int RESULT_LIMIT = 3;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final GoogleCalendarService googleCalendarService;

  RecommendationEngine(
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      GoogleCalendarService googleCalendarService) {
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.googleCalendarService = googleCalendarService;
  }

  // 방장이 고른 모드로 TOP3 후보를 계산 — 응답 참여자는 activeMembers 전원(모든 ACTIVE 멤버는 activate/join 시
  // 이미 일정 확인을 마쳤으므로 별도 미응답 제외 없음, 0건도 없음(방장은 항상 포함))
  List<RecommendationCandidate> generate(
      Trip trip,
      RecommendationMode mode,
      List<TripMember> activeMembers) {
    int durationDays = trip.getDurationDays();
    LocalDate rangeStart = trip.getStartRange();
    LocalDate rangeEnd = trip.getEndRange();
    Weights weights = Weights.forMode(mode);

    MemberContext context = loadContext(activeMembers, rangeStart, rangeEnd);

    List<RecommendationCandidate> scored = new ArrayList<>();
    for (LocalDate start = rangeStart; !start.plusDays(durationDays - 1L).isAfter(rangeEnd); start =
        start.plusDays(1)) {
      LocalDate end = start.plusDays(durationDays - 1L);
      scored.add(scoreCandidate(start, end, durationDays, activeMembers, context, weights));
    }

    scored.sort(
        Comparator.comparingDouble(RecommendationCandidate::score)
            .reversed()
            .thenComparingInt(RecommendationCandidate::uncertainScheduleCount)
            .thenComparing(RecommendationCandidate::startDate));

    return scored.stream().limit(RESULT_LIMIT).toList();
  }

  // 모드 무관 참여자 분류 — 특정 구간 하나만 다시 계산(추천 근거 상세 라이브 재계산·confirm 시점 통계 공용)
  List<MemberAttendanceDetail> classifyMembers(
      LocalDate startDate,
      LocalDate endDate,
      List<TripMember> activeMembers) {
    MemberContext context = loadContext(activeMembers, startDate, endDate);
    int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    List<MemberAttendanceDetail> details = new ArrayList<>();
    for (TripMember member : activeMembers) {
      UUID userId = member.getUser().getId();
      details.add(
          classifyOneMember(
              userId,
              startDate,
              totalDays,
              context.regularsByUser.getOrDefault(userId, List.of()),
              context.resolvedByUser.getOrDefault(userId, Map.of())));
    }
    return details;
  }

  // 후보 윈도우 하나에 대해 전 멤버를 분류하고 평가항목 4종 패널티·가중치를 적용해 점수를 계산
  private RecommendationCandidate scoreCandidate(
      LocalDate start,
      LocalDate end,
      int totalDays,
      List<TripMember> activeMembers,
      MemberContext context,
      Weights weights) {
    int respondedCount = activeMembers.size();
    int fullAttend = 0;
    int partialAttend = 0;
    int nonAttend = 0;
    int uncertainMembers = 0;
    int uncertainScheduleCount = 0;
    double totalVacationDays = 0;
    int vacationMemberCount = 0;

    for (TripMember member : activeMembers) {
      UUID userId = member.getUser().getId();
      MemberAttendanceDetail detail =
          classifyOneMember(
              userId,
              start,
              totalDays,
              context.regularsByUser.getOrDefault(userId, List.of()),
              context.resolvedByUser.getOrDefault(userId, Map.of()));

      switch (detail.attendance()) {
        case FULL_ATTEND -> fullAttend++;
        case PARTIAL_ATTEND -> partialAttend++;
        case NON_ATTEND -> nonAttend++;
      }
      if (detail.uncertainDays() > 0) {
        uncertainMembers++;
      }
      uncertainScheduleCount += detail.uncertainDays();
      if (detail.vacationDays() > 0) {
        totalVacationDays += detail.vacationDays();
        vacationMemberCount++;
      }
    }

    double nonAttendRate = (double) nonAttend / respondedCount;
    double partialAttendRate = (double) partialAttend / respondedCount;
    double uncertainRate = (double) uncertainMembers / respondedCount;
    double avgVacationDays = vacationMemberCount == 0 ? 0 : totalVacationDays / vacationMemberCount;

    double penaltySum =
        nonAttendPenalty(nonAttendRate) * weights.nonAttend()
            + partialAttendPenalty(partialAttendRate) * weights.partialAttend()
            + uncertainPenalty(uncertainRate) * weights.uncertain()
            + vacationPenalty(avgVacationDays) * weights.vacation();
    double score = 100 - penaltySum;

    int attendRate =
        (int) Math.round((double) (fullAttend + partialAttend) / respondedCount * 100);

    return new RecommendationCandidate(
        start, end, attendRate, partialAttend, uncertainMembers, totalVacationDays, score,
        uncertainScheduleCount);
  }

  // 참여자 1인 × 후보 구간 1개 — 전체참석/부분참석/불참 판정 + 불확실 일수 + 필요 연차일수
  private MemberAttendanceDetail classifyOneMember(
      UUID userId,
      LocalDate start,
      int totalDays,
      List<RegularSchedule> regulars,
      Map<LocalDate, CalendarDayResponse> resolved) {
    int totalSlots = totalDays * 3;
    boolean[] possible = new boolean[totalSlots];
    int uncertainDays = 0;

    for (int day = 0; day < totalDays; day++) {
      LocalDate date = start.plusDays(day);
      CalendarDayResponse resolvedDay = resolved.get(date);
      if (resolvedDay == null) {
        // 정기·개별·구글 신호가 전혀 없는 날짜 — 제약 없음(전부 가능)으로 취급
        possible[day * 3] = true;
        possible[day * 3 + 1] = true;
        possible[day * 3 + 2] = true;
        continue;
      }
      possible[day * 3] = resolvedDay.morningStatus() == ScheduleStatus.POSSIBLE;
      possible[day * 3 + 1] = resolvedDay.afternoonStatus() == ScheduleStatus.POSSIBLE;
      possible[day * 3 + 2] = resolvedDay.eveningStatus() == ScheduleStatus.POSSIBLE;
      if (resolvedDay.uncertain()) {
        uncertainDays++;
      }
    }

    LongestRun run = longestPossibleRun(possible);
    int threshold = (totalSlots + 1) / 2; // ⌈totalSlots * 0.5⌉

    AttendanceType attendance;
    int attendStartSlot;
    int attendEndSlot;
    if (run.length == totalSlots) {
      attendance = AttendanceType.FULL_ATTEND;
      attendStartSlot = 0;
      attendEndSlot = totalSlots - 1;
    } else if (run.length >= threshold) {
      attendance = AttendanceType.PARTIAL_ATTEND;
      attendStartSlot = run.start;
      attendEndSlot = run.start + run.length - 1;
    } else {
      attendance = AttendanceType.NON_ATTEND;
      attendStartSlot = -1;
      attendEndSlot = -1;
    }

    // 완전 불참자는 연차 계산에서 제외(BR-TRIP-005 특수 규칙)
    double vacationDays =
        attendance == AttendanceType.NON_ATTEND
            ? 0
            : vacationDaysForSpan(start, regulars, attendStartSlot, attendEndSlot);

    return new MemberAttendanceDetail(userId, attendance, uncertainDays, vacationDays);
  }

  // 오전/오후/저녁 슬롯 시퀀스에서 가능(POSSIBLE) 슬롯의 최장 연속 구간 — 부분참석은 이 값만으로 판정(분리된 구간 합산 금지)
  private static LongestRun longestPossibleRun(boolean[] possible) {
    int bestStart = -1;
    int bestLength = 0;
    int curStart = -1;
    int curLength = 0;
    for (int i = 0; i < possible.length; i++) {
      if (possible[i]) {
        if (curLength == 0) {
          curStart = i;
        }
        curLength++;
        if (curLength > bestLength) {
          bestLength = curLength;
          bestStart = curStart;
        }
      } else {
        curLength = 0;
      }
    }
    return new LongestRun(bestStart, bestLength);
  }

  private record LongestRun(
      int start,
      int length
  ) {
  }

  // 참석 구간 중 "정기 근무(오전/오후)만으로는 불가능(IMPOSSIBLE)"을 override로 참석 가능하게 만든 날을 반차(0.5)/종일(1.0)
  // 연차로 환산 — 저녁은 근무 개념이 없어 연차 계산에서 제외(반차 정의: 오전 반차·오후 반차·종일 연차)
  private double vacationDaysForSpan(
      LocalDate start,
      List<RegularSchedule> regulars,
      int attendStartSlot,
      int attendEndSlot) {
    if (attendStartSlot < 0) {
      return 0;
    }
    double total = 0;
    int firstDay = attendStartSlot / 3;
    int lastDay = attendEndSlot / 3;
    for (int day = firstDay; day <= lastDay; day++) {
      LocalDate date = start.plusDays(day);
      List<RegularSchedule> matched = matchingRegulars(regulars, date.getDayOfWeek());
      SlotStatuses regularOnly = ScheduleCalendarResolver.combineImpossibleWins(matched);
      int dayStartSlot = day * 3;
      int workSlotsCovered = 0;
      for (int offset = 0; offset < 2; offset++) { // 0=오전, 1=오후만 — 저녁 제외
        int slotIndex = dayStartSlot + offset;
        if (slotIndex < attendStartSlot || slotIndex > attendEndSlot) {
          continue;
        }
        ScheduleStatus regularStatus =
            offset == 0 ? regularOnly.getMorningStatus() : regularOnly.getAfternoonStatus();
        if (regularStatus == ScheduleStatus.IMPOSSIBLE) {
          workSlotsCovered++;
        }
      }
      if (workSlotsCovered == 2) {
        total += 1.0;
      } else if (workSlotsCovered == 1) {
        total += 0.5;
      }
    }
    return total;
  }

  private static List<RegularSchedule> matchingRegulars(
      List<RegularSchedule> regulars,
      DayOfWeek dayOfWeek) {
    return regulars.stream()
        .filter(
            regular -> ScheduleCalendarResolver
                .matchesDayOfWeek(regular.getDaysOfWeek(), dayOfWeek))
        .toList();
  }

  // 멤버별 regular/personal/구글 신호를 탐색 구간 전체에 대해 한 번만 로드 — 후보 윈도우마다 재조회하지 않음(N+1 방지)
  private MemberContext loadContext(
      List<TripMember> activeMembers,
      LocalDate rangeStart,
      LocalDate rangeEnd) {
    List<UUID> userIds = activeMembers.stream().map(member -> member.getUser().getId()).toList();

    Map<UUID, List<RegularSchedule>> regularsByUser =
        regularScheduleRepository.findByUserIdIn(userIds).stream()
            .collect(Collectors.groupingBy(regular -> regular.getUser().getId()));

    Map<UUID, List<PersonalSchedule>> personalsByUser =
        personalScheduleRepository
            .findByUserIdInAndScheduleDateBetween(userIds, rangeStart, rangeEnd).stream()
            .collect(Collectors.groupingBy(personal -> personal.getUser().getId()));

    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> busyByUser =
        googleCalendarService.findBusyDaysByUserIds(userIds, rangeStart, rangeEnd);

    Map<UUID, Map<LocalDate, CalendarDayResponse>> resolvedByUser = new HashMap<>();
    for (UUID userId : userIds) {
      List<CalendarDayResponse> resolved =
          ScheduleCalendarResolver.resolve(
              regularsByUser.getOrDefault(userId, List.of()),
              personalsByUser.getOrDefault(userId, List.of()),
              rangeStart,
              rangeEnd,
              busyByUser.getOrDefault(userId, Map.of()));
      resolvedByUser.put(
          userId,
          resolved.stream()
              .collect(Collectors.toMap(CalendarDayResponse::date, day -> day)));
    }

    return new MemberContext(regularsByUser, resolvedByUser);
  }

  private record MemberContext(
      Map<UUID, List<RegularSchedule>> regularsByUser,
      Map<UUID, Map<LocalDate, CalendarDayResponse>> resolvedByUser
  ) {
  }

  // 불참률 (scoring_draft.md 패널티 구간표 1)
  private static double nonAttendPenalty(double rate) {
    if (rate <= 0) {
      return 0;
    }
    if (rate <= 0.15) {
      return 20;
    }
    if (rate < 0.30) {
      return 50;
    }
    return 100;
  }

  // 부분 참석률 (구간표 2)
  private static double partialAttendPenalty(double rate) {
    if (rate <= 0) {
      return 0;
    }
    if (rate <= 0.15) {
      return 5;
    }
    if (rate < 0.30) {
      return 10;
    }
    return 20;
  }

  // 불확실 인원 비율 (구간표 3)
  private static double uncertainPenalty(double rate) {
    if (rate <= 0) {
      return 0;
    }
    if (rate <= 0.15) {
      return 10;
    }
    if (rate < 0.30) {
      return 20;
    }
    return 40;
  }

  // 1인당 평균 연차 일수 (구간표 4)
  private static double vacationPenalty(double avgDays) {
    if (avgDays <= 0) {
      return 0;
    }
    if (avgDays <= 0.5) {
      return 1;
    }
    if (avgDays <= 1) {
      return 3;
    }
    if (avgDays <= 2) {
      return 5;
    }
    return avgDays * 5;
  }

  // 모드별 가중치(불참률/부분참석비율/불확실인원비율/연차) — scoring_draft.md 확정값
  private record Weights(
      double nonAttend,
      double partialAttend,
      double uncertain,
      double vacation
  ) {

    static Weights forMode(RecommendationMode mode) {
      return switch (mode) {
        case BASIC -> new Weights(1.0, 1.0, 1.0, 1.0);
        case ALL_ATTEND -> new Weights(5.0, 3.0, 1.0, 0.5);
        case SAVE_VACATION -> new Weights(1.0, 0.5, 1.0, 5.0);
        case CERTAIN -> new Weights(1.0, 1.0, 5.0, 1.0);
      };
    }
  }
}
