package com.tripfit.tripfit.trip.recommendation.algorithm;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.schedule.domain.SlotStatuses;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityService;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityService.ScheduleAvailability;
import com.tripfit.tripfit.user.schedule.service.ScheduleCalendarResolver;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RecommendationEngine {

  private static final int RESULT_LIMIT = 3;

  private static final int HALF_DAYS_PER_DAY = 2;

  private static final int MAX_CONVERSION_UNITS = 20;

  private final ScheduleAvailabilityService scheduleAvailabilityService;

  private final HolidayProvider holidayProvider;

  public RecommendationEngine(
      ScheduleAvailabilityService scheduleAvailabilityService,
      HolidayProvider holidayProvider) {
    this.scheduleAvailabilityService = scheduleAvailabilityService;
    this.holidayProvider = holidayProvider;
  }

  public List<RecommendationCandidate> generate(
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

  public List<MemberAttendanceDetail> classifyMembers(
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
              context.personalsByUser.getOrDefault(userId, List.of()),
              context.googleBusyByUser.getOrDefault(userId, Map.of()),
              context.resolvedByUser.getOrDefault(userId, Map.of()),
              context.holidays(),
              context.usersByUser.get(userId)));
    }
    return details;
  }

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
              context.personalsByUser.getOrDefault(userId, List.of()),
              context.googleBusyByUser.getOrDefault(userId, Map.of()),
              context.resolvedByUser.getOrDefault(userId, Map.of()),
              context.holidays(),
              context.usersByUser.get(userId));

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

  private MemberAttendanceDetail classifyOneMember(
      UUID userId,
      LocalDate start,
      int totalDays,
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Map<LocalDate, CalendarDayResponse> resolved,
      Set<LocalDate> holidays,
      User user) {
    int totalSlots = totalDays * 3;
    boolean[] possible = new boolean[totalSlots];
    int uncertainDays = 0;

    for (int day = 0; day < totalDays; day++) {
      LocalDate date = start.plusDays(day);
      CalendarDayResponse resolvedDay = resolved.get(date);
      if (resolvedDay == null) {

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

    Map<LocalDate, PersonalSchedule> personalsByDate = indexPersonalsByDate(personals);
    possible = applyVacationSimulation(
        start,
        totalDays,
        possible,
        regulars,
        personalsByDate,
        googleBusyByDate,
        holidays,
        user);

    LongestRun run = longestPossibleRun(possible);
    int threshold = (totalSlots + 1) / 2;

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

    double vacationDays =
        attendance == AttendanceType.NON_ATTEND
            ? 0
            : vacationDaysForSpan(start, regulars, attendStartSlot, attendEndSlot, holidays, user);

    return new MemberAttendanceDetail(userId, attendance, uncertainDays, vacationDays);
  }

  private static Map<LocalDate, PersonalSchedule> indexPersonalsByDate(
      List<PersonalSchedule> personals) {
    Map<LocalDate, PersonalSchedule> byDate = new HashMap<>();
    for (PersonalSchedule personal : personals) {
      byDate.put(personal.getScheduleDate(), personal);
    }
    return byDate;
  }

  private boolean[] applyVacationSimulation(
      LocalDate start,
      int totalDays,
      boolean[] possible,
      List<RegularSchedule> regulars,
      Map<LocalDate, PersonalSchedule> personalsByDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Set<LocalDate> holidays,
      User user) {
    if (regulars.isEmpty()) {
      return possible;
    }
    int budgetHalfDays = user.getMaxVacationDays() * HALF_DAYS_PER_DAY;
    if (budgetHalfDays <= 0) {
      return possible;
    }

    VacationOptions options =
        collectVacationOptions(
            start,
            totalDays,
            possible,
            regulars,
            personalsByDate,
            googleBusyByDate,
            holidays,
            user.isHalfVacationAvailable(),
            user.isHolidayRest());
    int unitCount = options.unitCosts().length;
    if (unitCount == 0) {
      return possible;
    }

    boolean[] best = possible;
    int bestLength = longestPossibleRun(possible).length;
    int bestCost = 0;

    for (int mask = 1; mask < (1 << unitCount); mask++) {
      int cost = 0;
      for (int i = 0; i < unitCount; i++) {
        if ((mask & (1 << i)) != 0) {
          cost += options.unitCosts()[i];
        }
      }
      if (cost > budgetHalfDays) {
        continue;
      }
      boolean[] candidate = openSlots(possible, options.requirements(), mask);
      int length = longestPossibleRun(candidate).length;

      if (length > bestLength || (length == bestLength && cost < bestCost)) {
        best = candidate;
        bestLength = length;
        bestCost = cost;
      }
    }
    return best;
  }

  private static boolean[] openSlots(
      boolean[] possible,
      List<SlotRequirement> requirements,
      int mask) {
    boolean[] candidate = possible.clone();
    for (SlotRequirement requirement : requirements) {
      boolean allShiftsCleared = true;
      for (int unitMask : requirement.unitMasksByShift()) {
        if ((mask & unitMask) == 0) {
          allShiftsCleared = false;
          break;
        }
      }
      if (allShiftsCleared) {
        candidate[requirement.slotIndex()] = true;
      }
    }
    return candidate;
  }

  private static VacationOptions collectVacationOptions(
      LocalDate start,
      int totalDays,
      boolean[] possible,
      List<RegularSchedule> regulars,
      Map<LocalDate, PersonalSchedule> personalsByDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Set<LocalDate> holidays,
      boolean halfVacationAvailable,
      boolean holidayRest) {
    List<Integer> unitCosts = new ArrayList<>();
    List<SlotRequirement> requirements = new ArrayList<>();

    for (int day = 0; day < totalDays; day++) {
      LocalDate date = start.plusDays(day);
      List<RegularSchedule> matched = matchingRegulars(regulars, date, holidays, holidayRest);
      if (matched.isEmpty()) {
        continue;
      }
      PersonalSchedule personal = personalsByDate.get(date);
      GoogleCalendarBusyDay googleBusy = googleBusyByDate.get(date);

      boolean[] convertible = new boolean[3];
      for (int offset = 0; offset < 3; offset++) {
        convertible[offset] =
            !possible[day * 3 + offset] && !blockedByNonWorkReason(personal, googleBusy, offset);
      }

      List<List<Integer>> unitMasksBySlot = new ArrayList<>();
      for (int offset = 0; offset < 3; offset++) {
        unitMasksBySlot.add(new ArrayList<>());
      }
      for (RegularSchedule shift : matched) {
        addShiftUnits(shift, convertible, halfVacationAvailable, unitCosts, unitMasksBySlot);
      }
      for (int offset = 0; offset < 3; offset++) {
        List<Integer> unitMasksByShift = unitMasksBySlot.get(offset);
        if (!unitMasksByShift.isEmpty()) {
          requirements.add(new SlotRequirement(day * 3 + offset, List.copyOf(unitMasksByShift)));
        }
      }
    }

    int[] costs = new int[unitCosts.size()];
    for (int i = 0; i < costs.length; i++) {
      costs[i] = unitCosts.get(i);
    }
    return new VacationOptions(costs, List.copyOf(requirements));
  }

  private static void addShiftUnits(
      RegularSchedule shift,
      boolean[] convertible,
      boolean halfVacationAvailable,
      List<Integer> unitCosts,
      List<List<Integer>> unitMasksBySlot) {
    SlotStatuses own = shift.getSlotStatuses();
    boolean[] blocked = new boolean[3];
    boolean blocksAny = false;
    for (int offset = 0; offset < 3; offset++) {
      blocked[offset] =
          convertible[offset] && slotStatusByOffset(own, offset) == ScheduleStatus.IMPOSSIBLE;
      blocksAny |= blocked[offset];
    }
    if (!blocksAny || unitCosts.size() + 3 > MAX_CONVERSION_UNITS) {
      return;
    }

    int fullShiftUnit = 0;
    if (!halfVacationAvailable || blocked[2]) {
      fullShiftUnit = 1 << unitCosts.size();
      unitCosts.add(HALF_DAYS_PER_DAY);
    }
    int morningUnit = 0;
    if (halfVacationAvailable && blocked[0]) {
      morningUnit = 1 << unitCosts.size();
      unitCosts.add(1);
    }
    int afternoonUnit = 0;
    if (halfVacationAvailable && blocked[1]) {
      afternoonUnit = 1 << unitCosts.size();
      unitCosts.add(1);
    }

    if (blocked[0]) {
      unitMasksBySlot.get(0).add(fullShiftUnit | morningUnit);
    }
    if (blocked[1]) {
      unitMasksBySlot.get(1).add(fullShiftUnit | afternoonUnit);
    }
    if (blocked[2]) {
      unitMasksBySlot.get(2).add(fullShiftUnit);
    }
  }

  private static boolean blockedByNonWorkReason(
      PersonalSchedule personal,
      GoogleCalendarBusyDay googleBusy,
      int offset) {
    if (personal != null && personal.getSlotStatuses() != null) {
      ScheduleStatus override = slotStatusByOffset(personal.getSlotStatuses(), offset);
      if (override != null) {
        return true;
      }
    }
    if (googleBusy == null) {
      return false;
    }
    return switch (offset) {
      case 0 -> googleBusy.isMorningBusy();
      case 1 -> googleBusy.isAfternoonBusy();
      default -> googleBusy.isEveningBusy();
    };
  }

  private static ScheduleStatus slotStatusByOffset(SlotStatuses statuses, int offset) {
    return switch (offset) {
      case 0 -> statuses.getMorningStatus();
      case 1 -> statuses.getAfternoonStatus();
      default -> statuses.getEveningStatus();
    };
  }

  private record SlotRequirement(
      int slotIndex,
      List<Integer> unitMasksByShift
  ) {
  }

  private record VacationOptions(
      int[] unitCosts,
      List<SlotRequirement> requirements
  ) {
  }

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

  private double vacationDaysForSpan(
      LocalDate start,
      List<RegularSchedule> regulars,
      int attendStartSlot,
      int attendEndSlot,
      Set<LocalDate> holidays,
      User user) {
    if (attendStartSlot < 0) {
      return 0;
    }
    boolean halfVacationAvailable = user.isHalfVacationAvailable();
    double total = 0;
    int firstDay = attendStartSlot / 3;
    int lastDay = attendEndSlot / 3;
    for (int day = firstDay; day <= lastDay; day++) {
      LocalDate date = start.plusDays(day);
      for (RegularSchedule shift : matchingRegulars(
          regulars,
          date,
          holidays,
          user.isHolidayRest())) {
        total +=
            vacationDaysForShift(shift, day, attendStartSlot, attendEndSlot, halfVacationAvailable);
      }
    }
    return total;
  }

  private static double vacationDaysForShift(
      RegularSchedule shift,
      int day,
      int attendStartSlot,
      int attendEndSlot,
      boolean halfVacationAvailable) {
    SlotStatuses own = shift.getSlotStatuses();
    boolean[] blocksWithinSpan = new boolean[3];
    for (int offset = 0; offset < 3; offset++) {
      int slotIndex = day * 3 + offset;
      blocksWithinSpan[offset] =
          slotIndex >= attendStartSlot
              && slotIndex <= attendEndSlot
              && slotStatusByOffset(own, offset) == ScheduleStatus.IMPOSSIBLE;
    }
    if (blocksWithinSpan[2] || (blocksWithinSpan[0] && blocksWithinSpan[1])) {
      return 1.0;
    }
    if (blocksWithinSpan[0] || blocksWithinSpan[1]) {
      return halfVacationAvailable ? 0.5 : 1.0;
    }
    return 0;
  }

  private static List<RegularSchedule> matchingRegulars(
      List<RegularSchedule> regulars,
      LocalDate date,
      Set<LocalDate> holidays,
      boolean holidayRest) {
    if (holidays.contains(date) && holidayRest) {
      return List.of();
    }
    return regulars.stream()
        .filter(
            regular -> ScheduleCalendarResolver
                .matchesDayOfWeek(regular.getDaysOfWeek(), date.getDayOfWeek()))
        .toList();
  }

  private MemberContext loadContext(
      List<TripMember> activeMembers,
      LocalDate rangeStart,
      LocalDate rangeEnd) {
    List<UUID> userIds = activeMembers.stream().map(member -> member.getUser().getId()).toList();

    Map<UUID, User> usersByUser =
        activeMembers.stream()
            .collect(Collectors.toMap(member -> member.getUser().getId(), TripMember::getUser));

    Map<UUID, List<RegularSchedule>> regularsByUser =
        scheduleAvailabilityService.findRegularSchedulesByUserIds(userIds);

    Map<UUID, List<PersonalSchedule>> personalsByUser =
        scheduleAvailabilityService.findPersonalSchedulesByUserIds(userIds, rangeStart, rangeEnd);

    ScheduleAvailability availability =
        scheduleAvailabilityService.resolveAvailability(userIds, rangeStart, rangeEnd);
    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> busyByUser = availability.googleBusyByUser();
    Map<UUID, List<CalendarDayResponse>> mergedByUser = availability.mergedByUser();

    Map<UUID, Map<LocalDate, CalendarDayResponse>> resolvedByUser = new HashMap<>();
    for (UUID userId : userIds) {
      resolvedByUser.put(
          userId,
          mergedByUser.getOrDefault(userId, List.of()).stream()
              .collect(Collectors.toMap(CalendarDayResponse::date, day -> day)));
    }

    Set<LocalDate> holidays = holidayProvider.findHolidaysBetween(rangeStart, rangeEnd);

    return new MemberContext(
        regularsByUser, personalsByUser, busyByUser, resolvedByUser, holidays, usersByUser);
  }

  private record MemberContext(
      Map<UUID, List<RegularSchedule>> regularsByUser,
      Map<UUID, List<PersonalSchedule>> personalsByUser,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser,
      Map<UUID, Map<LocalDate, CalendarDayResponse>> resolvedByUser,
      Set<LocalDate> holidays,
      Map<UUID, User> usersByUser
  ) {
  }

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
