package com.tripfit.tripfit.trip.recommendation.algorithm;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.recommendation.domain.AttendanceType;
import com.tripfit.tripfit.trip.recommendation.domain.RecommendationMode;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.schedule.domain.SlotStatuses;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.port.out.GoogleCalendarPort;
import com.tripfit.tripfit.trip.port.out.SchedulePort;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
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

// 후보 윈도우 생성 → 연차/반차 자동 전환 시뮬레이션 → 참여자 3분류 → 평가항목 4종 패널티·모드 가중치 스코어링
// → 동점 처리 → Best3 산출(#50 BR-TRIP-005·011·012, #105 연차 자동 반영)
@Component
public class RecommendationEngine {

  private static final int RESULT_LIMIT = 3;

  // 연차 비용은 반차(0.5일)를 정수로 다루기 위해 0.5일 단위로 계산 — 종일 연차 1일 = 2
  private static final int HALF_DAYS_PER_DAY = 2;

  // 전환 단위 조합은 완전탐색(2^n)이라 단위 수에 상한을 둔다 — 후보 윈도우가 며칠이고 정기 일정이 몇 행인
  // 실사용 범위는 넉넉히 들어오지만, 극단적으로 긴 구간에서 조합 수가 폭발하는 것은 막는다
  private static final int MAX_CONVERSION_UNITS = 20;

  private final SchedulePort schedulePort;

  private final GoogleCalendarPort googleCalendarPort;

  private final HolidayProvider holidayProvider;

  public RecommendationEngine(
      SchedulePort schedulePort,
      GoogleCalendarPort googleCalendarPort,
      HolidayProvider holidayProvider) {
    this.schedulePort = schedulePort;
    this.googleCalendarPort = googleCalendarPort;
    this.holidayProvider = holidayProvider;
  }

  // 방장이 고른 모드로 TOP3 후보를 계산 — 응답 참여자는 activeMembers 전원(모든 ACTIVE 멤버는 activate/join 시
  // 이미 일정 확인을 마쳤으므로 별도 미응답 제외 없음, 0건도 없음(방장은 항상 포함))
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

  // 모드 무관 참여자 분류 — 특정 구간 하나만 다시 계산(추천 근거 상세 라이브 재계산·confirm 시점 통계 공용)
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
              context.holidays()));
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
              context.personalsByUser.getOrDefault(userId, List.of()),
              context.googleBusyByUser.getOrDefault(userId, Map.of()),
              context.resolvedByUser.getOrDefault(userId, Map.of()),
              context.holidays());

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

  // 참여자 1인 × 후보 구간 1개 — 연차/반차 자동 전환 시뮬레이션 적용 후 전체참석/부분참석/불참 판정 + 불확실 일수
  // + 필요 연차일수 (#105)
  private MemberAttendanceDetail classifyOneMember(
      UUID userId,
      LocalDate start,
      int totalDays,
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Map<LocalDate, CalendarDayResponse> resolved,
      Set<LocalDate> holidays) {
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

    Map<LocalDate, PersonalSchedule> personalsByDate = indexPersonalsByDate(personals);
    possible = applyVacationSimulation(
        start,
        totalDays,
        possible,
        regulars,
        personalsByDate,
        googleBusyByDate,
        holidays);

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
            : vacationDaysForSpan(start, regulars, attendStartSlot, attendEndSlot, holidays);

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

  // 정기 근무로만 막힌 슬롯을, 참여자의 연차 예산(첫 번째로 등록된 RegularSchedule 기준 — #105 "필드 이동" 전
  // 임시 규칙, #52) 안에서 최장 연속 참석 구간이 가장 길어지는 조합으로 자동 전환한다. 개별 일정·구글 busy로
  // 막힌 슬롯은 대상에서 제외(연차는 근무만 대체 가능)
  private boolean[] applyVacationSimulation(
      LocalDate start,
      int totalDays,
      boolean[] possible,
      List<RegularSchedule> regulars,
      Map<LocalDate, PersonalSchedule> personalsByDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Set<LocalDate> holidays) {
    if (regulars.isEmpty()) {
      return possible;
    }
    RegularSchedule primary = RegularSchedule.policySource(regulars).orElseThrow();
    int budgetHalfDays = primary.getMaxVacationDays() * HALF_DAYS_PER_DAY;
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
            primary.isHalfVacationAvailable());
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
      // 1. 최장 연속 구간이 더 긴 조합 우선 2. 길이가 같으면 연차를 덜 쓰는 조합 우선(여분 연차 낭비 방지)
      if (length > bestLength || (length == bestLength && cost < bestCost)) {
        best = candidate;
        bestLength = length;
        bestCost = cost;
      }
    }
    return best;
  }

  // 고른 전환 단위 조합(mask)으로 실제 열리는 슬롯을 계산 — 한 슬롯을 여러 근무가 동시에 막고 있으면
  // 그 근무를 전부 빼야 열린다(하나만 빼도 나머지 근무가 그대로 막고 있으므로)
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

  // 연차 전환 후보를 "근무(정기 일정 한 행) 단위"로 만든다 — 종일 연차 1일은 그 근무가 막는 슬롯을 오전·오후·
  // 저녁 가릴 것 없이 통째로 열고("근무 사이클 하나를 전부 가능으로"), 반차 0.5일은 그 근무의 오전 또는 오후
  // 반나절만 연다. "저녁 반차"는 없으므로(기획 확정: 오전 반차·오후 반차·종일 연차 셋뿐) 저녁이 걸린 근무를
  // 저녁까지 열려면 항상 종일 연차를 쓴다. 개별 일정 override·구글 busy로 막힌 슬롯은 연차 대상이 아니라 제외
  private static VacationOptions collectVacationOptions(
      LocalDate start,
      int totalDays,
      boolean[] possible,
      List<RegularSchedule> regulars,
      Map<LocalDate, PersonalSchedule> personalsByDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      Set<LocalDate> holidays,
      boolean halfVacationAvailable) {
    List<Integer> unitCosts = new ArrayList<>();
    List<SlotRequirement> requirements = new ArrayList<>();

    for (int day = 0; day < totalDays; day++) {
      LocalDate date = start.plusDays(day);
      List<RegularSchedule> matched = matchingRegulars(regulars, date, holidays);
      if (matched.isEmpty()) {
        continue;
      }
      PersonalSchedule personal = personalsByDate.get(date);
      GoogleCalendarBusyDay googleBusy = googleBusyByDate.get(date);

      // 1. 그날 연차로 열 수 있는 슬롯 — 아직 불가능하고, 막힌 이유가 개인 일정·구글 일정이 아닌 것만
      boolean[] convertible = new boolean[3];
      for (int offset = 0; offset < 3; offset++) {
        convertible[offset] =
            !possible[day * 3 + offset] && !blockedByNonWorkReason(personal, googleBusy, offset);
      }

      // 2. 근무마다 전환 단위를 만들고, 슬롯별로 "그 근무를 어떤 단위로 사야 열리는지" 비트마스크를 모은다
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

  // 근무 한 행이 그날 막고 있는 슬롯에 대한 전환 단위를 만든다 — 종일 연차(1일)와, 반차가 가능하면 그 근무의
  // 오전·오후 반차(각 0.5일). 저녁이 안 걸린 근무는 반차 둘로 같은 범위를 같은 값에 살 수 있어 종일 연차 단위를
  // 따로 만들지 않는다(완전탐색 조합 수 절약). 조합 폭발을 막기 위해 단위 수는 MAX_CONVERSION_UNITS까지만 만든다
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

  // 이 슬롯의 IMPOSSIBLE이 정기 근무가 아니라 개별 일정 override나 구글 캘린더 busy 때문인지 확인 —
  // 둘 중 하나라도 해당하면 연차로 전환할 수 없다(연차는 근무만 대체 가능)
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

  // 연차로 열 수 있는 슬롯 하나의 해제 조건 — 이 슬롯을 막는 근무마다 마스크가 하나씩 들어있고, 마스크마다
  // 최소 한 단위씩 사야(= 막는 근무를 전부 빼야) 슬롯이 열린다
  private record SlotRequirement(
      int slotIndex,
      List<Integer> unitMasksByShift
  ) {
  }

  // 전환 단위별 비용(0.5일 단위, 인덱스=단위 번호)과 슬롯별 해제 조건
  private record VacationOptions(
      int[] unitCosts,
      List<SlotRequirement> requirements
  ) {
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

  // 참석 구간 안에서 정기 근무와 겹치는 부분을 없애는 데 든 연차를 근무(정기 일정 한 행) 단위로 합산 — 수동
  // override와 applyVacationSimulation의 자동 전환 둘 다 possible[]을 POSSIBLE로 바꿔놓으므로, 이 메서드는
  // "참석 구간에 걸린 근무 시간대"만 보고 동일하게 환산한다(전환 방식을 구분할 필요 없음). 근무를 2개 등록한
  // 사용자(회사 + 저녁 알바 등)는 근무마다 따로 연차를 써야 하므로 각각 더한다
  private double vacationDaysForSpan(
      LocalDate start,
      List<RegularSchedule> regulars,
      int attendStartSlot,
      int attendEndSlot,
      Set<LocalDate> holidays) {
    if (attendStartSlot < 0) {
      return 0;
    }
    boolean halfVacationAvailable =
        RegularSchedule.policySource(regulars)
            .map(RegularSchedule::isHalfVacationAvailable)
            .orElse(false);
    double total = 0;
    int firstDay = attendStartSlot / 3;
    int lastDay = attendEndSlot / 3;
    for (int day = firstDay; day <= lastDay; day++) {
      LocalDate date = start.plusDays(day);
      for (RegularSchedule shift : matchingRegulars(regulars, date, holidays)) {
        total +=
            vacationDaysForShift(shift, day, attendStartSlot, attendEndSlot, halfVacationAvailable);
      }
    }
    return total;
  }

  // 근무 하나를 참석 구간만큼 빼는 데 드는 연차 — 저녁이 걸리면 "저녁 반차"라는 상품이 없어 근무 사이클 전체를
  // 빼야 하므로 종일 연차 1일, 오전·오후를 둘 다 빼도 1일(반차 두 번 = 종일 연차 한 번), 반나절 하나만 빼면
  // 반차 0.5일. 반차 불가 사용자는 반나절 하나만 빼도 종일 연차 1일이 든다(#105 특수 규칙 8)
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

  // 그날 실제로 적용되는 정기 근무 — 공휴일에 쉬는 사용자의 공휴일은 근무가 없는 날로 본다. 이 필터가 없으면
  // vacationDaysForSpan이 공휴일에도 근무가 걸린 것으로 계산해, 쉬는 사람에게 연차를 청구하고 점수를 왜곡시킨다
  private static List<RegularSchedule> matchingRegulars(
      List<RegularSchedule> regulars,
      LocalDate date,
      Set<LocalDate> holidays) {
    if (holidays.contains(date) && RegularSchedule.restsOnHolidays(regulars)) {
      return List.of();
    }
    return regulars.stream()
        .filter(
            regular -> ScheduleCalendarResolver
                .matchesDayOfWeek(regular.getDaysOfWeek(), date.getDayOfWeek()))
        .toList();
  }


  // 멤버별 regular/personal/구글 신호를 탐색 구간 전체에 대해 한 번만 로드 — 후보 윈도우마다 재조회하지 않음(N+1 방지)
  private MemberContext loadContext(
      List<TripMember> activeMembers,
      LocalDate rangeStart,
      LocalDate rangeEnd) {
    List<UUID> userIds = activeMembers.stream().map(member -> member.getUser().getId()).toList();

    Map<UUID, List<RegularSchedule>> regularsByUser =
        schedulePort.findRegularSchedulesByUserIds(userIds);

    Map<UUID, List<PersonalSchedule>> personalsByUser =
        schedulePort.findPersonalSchedulesByUserIds(userIds, rangeStart, rangeEnd);

    Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> busyByUser =
        googleCalendarPort.findBusyDaysByUserIds(userIds, rangeStart, rangeEnd);

    Map<UUID, List<CalendarDayResponse>> mergedByUser =
        schedulePort.resolveMergedSchedules(userIds, rangeStart, rangeEnd, busyByUser);

    Map<UUID, Map<LocalDate, CalendarDayResponse>> resolvedByUser = new HashMap<>();
    for (UUID userId : userIds) {
      resolvedByUser.put(
          userId,
          mergedByUser.getOrDefault(userId, List.of()).stream()
              .collect(Collectors.toMap(CalendarDayResponse::date, day -> day)));
    }

    // 공휴일은 전 멤버 공통이라 탐색 구간 전체에 대해 한 번만 조회한다
    Set<LocalDate> holidays = holidayProvider.findHolidaysBetween(rangeStart, rangeEnd);

    return new MemberContext(
        regularsByUser, personalsByUser, busyByUser, resolvedByUser, holidays);
  }

  private record MemberContext(
      Map<UUID, List<RegularSchedule>> regularsByUser,
      Map<UUID, List<PersonalSchedule>> personalsByUser,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser,
      Map<UUID, Map<LocalDate, CalendarDayResponse>> resolvedByUser,
      Set<LocalDate> holidays
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
