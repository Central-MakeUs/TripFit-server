package com.tripfit.tripfit.trip.recommendation.algorithm;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 후보 윈도우 생성 → 연차/반차 자동 전환 시뮬레이션 → 참여자 3분류 → 평가항목 4종 패널티·모드 가중치 스코어링
// → 동점 처리 → Best3 산출(#50 BR-TRIP-005·011·012, #105 연차 자동 반영)
@Component
public class RecommendationEngine {

  private static final int RESULT_LIMIT = 3;

  private final SchedulePort schedulePort;

  private final GoogleCalendarPort googleCalendarPort;

  public RecommendationEngine(
      SchedulePort schedulePort,
      GoogleCalendarPort googleCalendarPort) {
    this.schedulePort = schedulePort;
    this.googleCalendarPort = googleCalendarPort;
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
              context.personalsByUser.getOrDefault(userId, List.of()),
              context.googleBusyByUser.getOrDefault(userId, Map.of()),
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

  // 참여자 1인 × 후보 구간 1개 — 연차/반차 자동 전환 시뮬레이션 적용 후 전체참석/부분참석/불참 판정 + 불확실 일수
  // + 필요 연차일수 (#105)
  private MemberAttendanceDetail classifyOneMember(
      UUID userId,
      LocalDate start,
      int totalDays,
      List<RegularSchedule> regulars,
      List<PersonalSchedule> personals,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
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

    Map<LocalDate, PersonalSchedule> personalsByDate = indexPersonalsByDate(personals);
    possible = applyVacationSimulation(
        start,
        totalDays,
        possible,
        regulars,
        personalsByDate,
        googleBusyByDate);

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
  // 막힌 슬롯은 대상에서 제외(연차는 근무만 대체 가능). 오전·오후가 둘 다 뚫린 날은 openFreeEvenings로
  // 저녁도 함께 열어준다(퇴근이 늦어 저녁까지 근무로 막혀있던 케이스, #105 amend)
  private boolean[] applyVacationSimulation(
      LocalDate start,
      int totalDays,
      boolean[] possible,
      List<RegularSchedule> regulars,
      Map<LocalDate, PersonalSchedule> personalsByDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate) {
    if (regulars.isEmpty()) {
      return possible;
    }
    RegularSchedule primary = primaryVacationSchedule(regulars).orElseThrow();
    int budgetHalfDays = primary.getMaxVacationDays() * 2;
    if (budgetHalfDays <= 0) {
      return possible;
    }

    List<ConversionUnit> units = collectConversionUnits(
        start,
        totalDays,
        possible,
        regulars,
        personalsByDate,
        googleBusyByDate,
        primary.isHalfVacationAvailable());
    if (units.isEmpty()) {
      return possible;
    }

    boolean[] best =
        openFreeEvenings(
            possible.clone(),
            start,
            totalDays,
            regulars,
            personalsByDate,
            googleBusyByDate);
    int bestLength = longestPossibleRun(best).length;
    int bestCost = 0;

    int unitCount = units.size();
    for (int mask = 1; mask < (1 << unitCount); mask++) {
      int cost = 0;
      for (int i = 0; i < unitCount; i++) {
        if ((mask & (1 << i)) != 0) {
          cost += units.get(i).costHalfDays();
        }
      }
      if (cost > budgetHalfDays) {
        continue;
      }
      boolean[] candidate = possible.clone();
      for (int i = 0; i < unitCount; i++) {
        if ((mask & (1 << i)) != 0) {
          for (int slotIndex : units.get(i).slotIndices()) {
            candidate[slotIndex] = true;
          }
        }
      }
      candidate =
          openFreeEvenings(
              candidate,
              start,
              totalDays,
              regulars,
              personalsByDate,
              googleBusyByDate);
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

  // 그날 오전·오후가 (연차 전환 포함) 둘 다 뚫려서 근무로 인한 막힘이 완전히 해소됐다면, 같은 근무 시간이
  // 저녁까지 걸쳐 있어서 저녁만 따로 IMPOSSIBLE로 남아있던 슬롯도 함께 연다 — 오전·오후 중 하나만 뚫린
  // 반차 케이스는 나머지 반나절 근무가 저녁까지 이어질 수 있으므로 건드리지 않는다(#105 amend). 개별
  // 일정 override·구글 busy로 저녁이 막힌 경우는 연차와 무관하므로 대상에서 제외
  private static boolean[] openFreeEvenings(
      boolean[] possible,
      LocalDate start,
      int totalDays,
      List<RegularSchedule> regulars,
      Map<LocalDate, PersonalSchedule> personalsByDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate) {
    for (int day = 0; day < totalDays; day++) {
      LocalDate date = start.plusDays(day);
      List<RegularSchedule> matched = matchingRegulars(regulars, date.getDayOfWeek());
      if (matched.isEmpty()) {
        continue;
      }
      if (!hasContinuousShiftIntoEvening(matched)) {
        continue;
      }
      if (blockedByNonWorkReason(personalsByDate.get(date), googleBusyByDate.get(date), 2)) {
        continue;
      }
      int morningSlot = day * 3;
      int afternoonSlot = day * 3 + 1;
      if (possible[morningSlot] && possible[afternoonSlot]) {
        possible[day * 3 + 2] = true;
      }
    }
    return possible;
  }

  // 그날 매칭되는 정기 일정 중, 한 행(row) 혼자서 오전·오후 중 하나와 저녁을 동시에 막는 "연속 근무"가
  // 있는지 확인 — 예: 10~19시 근무 1건. 서로 다른 두 행이 각자 오전/오후·저녁을 나눠 막는 경우(예: 낮에는
  // A 근무, 저녁에는 B 알바)는 대상에서 제외한다. A 연차는 A만 대체할 뿐 B 근무까지 자동으로 없애주지
  // 않기 때문(#105 후속 확인)
  private static boolean hasContinuousShiftIntoEvening(List<RegularSchedule> matched) {
    for (RegularSchedule regular : matched) {
      SlotStatuses own = regular.getSlotStatuses();
      boolean spansDaytime =
          own.getMorningStatus() == ScheduleStatus.IMPOSSIBLE
              || own.getAfternoonStatus() == ScheduleStatus.IMPOSSIBLE;
      if (spansDaytime && own.getEveningStatus() == ScheduleStatus.IMPOSSIBLE) {
        return true;
      }
    }
    return false;
  }

  // 연차 전환 후보 슬롯을 하루 단위로 묶어 전환 비용 단위(ConversionUnit)로 만든다 — 반차 가능 유저는
  // 오전/오후 슬롯을 0.5일씩 독립 전환, 반차 불가 유저는 하루 단위로 묶어 1.0일에 전부 전환(#105 반차 규칙)
  private static List<ConversionUnit> collectConversionUnits(
      LocalDate start,
      int totalDays,
      boolean[] possible,
      List<RegularSchedule> regulars,
      Map<LocalDate, PersonalSchedule> personalsByDate,
      Map<LocalDate, GoogleCalendarBusyDay> googleBusyByDate,
      boolean halfVacationAvailable) {
    List<ConversionUnit> units = new ArrayList<>();
    for (int day = 0; day < totalDays; day++) {
      LocalDate date = start.plusDays(day);
      List<RegularSchedule> matched = matchingRegulars(regulars, date.getDayOfWeek());
      if (matched.isEmpty()) {
        continue;
      }
      SlotStatuses regularOnly = ScheduleCalendarResolver.combineImpossibleWins(matched);
      PersonalSchedule personal = personalsByDate.get(date);
      GoogleCalendarBusyDay googleBusy = googleBusyByDate.get(date);

      List<Integer> dayEligibleSlots = new ArrayList<>();
      for (int offset = 0; offset < 2; offset++) { // 0=오전, 1=오후 — 저녁은 연차 대상 아님
        int slotIndex = day * 3 + offset;
        if (possible[slotIndex]) {
          continue;
        }
        ScheduleStatus regularStatus =
            offset == 0 ? regularOnly.getMorningStatus() : regularOnly.getAfternoonStatus();
        if (regularStatus != ScheduleStatus.IMPOSSIBLE) {
          continue;
        }
        if (blockedByNonWorkReason(personal, googleBusy, offset)) {
          continue;
        }
        dayEligibleSlots.add(slotIndex);
      }
      if (dayEligibleSlots.isEmpty()) {
        continue;
      }
      if (halfVacationAvailable) {
        for (int slotIndex : dayEligibleSlots) {
          units.add(new ConversionUnit(List.of(slotIndex), 1));
        }
      } else {
        units.add(new ConversionUnit(dayEligibleSlots, 2));
      }
    }
    return units;
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

  // 연차 전환 후보 슬롯 묶음 — slotIndices를 전부 전환하는 데 costHalfDays(0.5일 단위)가 든다
  private record ConversionUnit(
      List<Integer> slotIndices,
      int costHalfDays
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

  // 참석 구간 중 "정기 근무(오전/오후)만으로는 불가능(IMPOSSIBLE)"을 override로 참석 가능하게 만든 날을 반차(0.5)/종일(1.0)
  // 연차로 환산 — 저녁은 근무 개념이 없어 연차 계산에서 제외(반차 정의: 오전 반차·오후 반차·종일 연차). 수동 override와
  // applyVacationSimulation의 자동 전환 둘 다 possible[]을 POSSIBLE로 바꿔놓으므로 이 메서드는 그 결과만 보고
  // 동일하게 집계한다(전환 방식을 구분할 필요 없음). halfVacationAvailable=false면 반나절 하나만 막혔어도
  // 반차를 못 쓰니 종일 연차 1.0으로 계산한다(#105 특수 규칙 8)
  private double vacationDaysForSpan(
      LocalDate start,
      List<RegularSchedule> regulars,
      int attendStartSlot,
      int attendEndSlot) {
    if (attendStartSlot < 0) {
      return 0;
    }
    boolean halfVacationAvailable =
        primaryVacationSchedule(regulars)
            .map(RegularSchedule::isHalfVacationAvailable)
            .orElse(false);
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
        total += halfVacationAvailable ? 0.5 : 1.0;
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

  // 연차 예산·반차 가능 여부의 기준이 되는 "대표" 정기 일정 — 가장 먼저 등록된 행(#52로 User 이동 전 임시 규칙).
  // 실제 클라이언트가 저장 시점에 모든 행에 동일한 값을 다시 써서 항상 일치시키므로(TripFit-client
  // useSaveRegularSchedule), 어느 행을 골라도 결과는 같다
  private static Optional<RegularSchedule> primaryVacationSchedule(
      List<RegularSchedule> regulars) {
    return regulars.stream().min(Comparator.comparing(RegularSchedule::getCreatedAt));
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

    return new MemberContext(regularsByUser, personalsByUser, busyByUser, resolvedByUser);
  }

  private record MemberContext(
      Map<UUID, List<RegularSchedule>> regularsByUser,
      Map<UUID, List<PersonalSchedule>> personalsByUser,
      Map<UUID, Map<LocalDate, GoogleCalendarBusyDay>> googleBusyByUser,
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
