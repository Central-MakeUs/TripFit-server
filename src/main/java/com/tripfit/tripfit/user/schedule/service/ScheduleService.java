package com.tripfit.tripfit.user.schedule.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.Weekday;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.dto.CreateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse.PersonalScheduleItemResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse.RegularScheduleListResponse;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest.PersonalScheduleItem;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest.SlotUpdate;
import com.tripfit.tripfit.user.schedule.dto.ScheduleCalendarResponse.CalendarDayResponse;
import com.tripfit.tripfit.user.schedule.dto.UpdateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdateVacationPolicyRequest;
import com.tripfit.tripfit.user.schedule.dto.VacationPolicyResponse;
import com.tripfit.tripfit.user.schedule.exception.ScheduleErrorCode;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사용자 정기·개별 일정 CRUD와 합산 달력 조회 — 정기 일정 없이 개별만 등록 가능
// hasPreSchedule은 본 Service 응답에 없음 — row INSERT/DELETE 후 UserSummaryService EXISTS → GET /auth/me 등
// 재조회
@Service
@RequiredArgsConstructor
public class ScheduleService {

  // 마이페이지 달력 조회 가능 기간(년) — today ~ today+2년−1
  public static final int CALENDAR_WINDOW_YEARS = 2;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserLookupService userLookupService;

  private final UserSummaryService userSummaryService;

  private final GoogleCalendarService googleCalendarService;

  private final TripMemberRepository tripMemberRepository;

  private final HolidayProvider holidayProvider;

  // 정기 일정 목록 조회 — 생성 시각 오름차순
  @Transactional(readOnly = true)
  public RegularScheduleListResponse listRegular(UUID userId) {
    return new RegularScheduleListResponse(
        regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .map(this::toRegularResponse)
            .toList());
  }

  // 정기 일정 생성 — start/end로 슬롯 계산 후 저장, 첫 row면 hasPreSchedule true(다음 login/me/profile 재조회)
  @Transactional
  public RegularScheduleResponse createRegular(UUID userId, CreateRegularScheduleRequest request) {
    // 1. 제목·시각 입력을 검증함
    validateRegularTimes(
        request.title(), request.daysOfWeek(), request.startTime(), request.endTime());

    // 2. start/end로 슬롯을 계산해 정기 일정을 저장함
    User user = userLookupService.requireUser(userId);
    RegularSchedule schedule =
        RegularSchedule.create(
            user,
            request.title().trim(),
            normalizeDaysOfWeek(request.daysOfWeek()),
            request.startTime(),
            request.endTime());
    regularScheduleRepository.save(schedule);
    userSummaryService.clearAllFreeOnScheduleAdded(user);
    return toRegularResponse(schedule);
  }

  // 정기 일정 전체 수정 — start/end로 슬롯 재계산
  @Transactional
  public RegularScheduleResponse updateRegular(
      UUID userId,
      UUID regularId,
      UpdateRegularScheduleRequest request) {
    validateRegularTimes(
        request.title(), request.daysOfWeek(), request.startTime(), request.endTime());
    RegularSchedule schedule = requireOwnedRegularSchedule(regularId, userId);
    schedule.applyUpdate(
        request.title().trim(),
        normalizeDaysOfWeek(request.daysOfWeek()),
        request.startTime(),
        request.endTime());
    return toRegularResponse(schedule);
  }

  // 연차·반차·공휴일 휴무 설정 조회
  @Transactional(readOnly = true)
  public VacationPolicyResponse getVacationPolicy(UUID userId) {
    return toVacationPolicyResponse(userLookupService.requireUser(userId));
  }

  // 연차·반차·공휴일 휴무 설정 전체 교체(부분 patch 아님) — isAllFree는 건드리지 않는다(일정 등록이 아님)
  @Transactional
  public VacationPolicyResponse updateVacationPolicy(
      UUID userId, UpdateVacationPolicyRequest request) {
    validateVacationPolicy(request.maxVacationDays());
    User user = userLookupService.requireUser(userId);
    user.applyVacationPolicy(
        request.maxVacationDays(),
        request.vacationApplyPeriod(),
        request.halfVacationAvailable(),
        request.holidayRest());
    return toVacationPolicyResponse(user);
  }

  // 정기 일정 삭제 — regular 0건 + personal 0건이면 hasPreSchedule false (다음 login/me/profile)
  @Transactional
  public void deleteRegular(UUID userId, UUID regularId) {
    RegularSchedule schedule = requireOwnedRegularSchedule(regularId, userId);
    regularScheduleRepository.delete(schedule);
    userSummaryService.markAllFreeIfNoSchedules(userLookupService.requireUser(userId));
  }

  // 본인 소유 정기 일정 로드 — 없거나 타인 소유면 REGULAR_SCHEDULE_NOT_FOUND
  private RegularSchedule requireOwnedRegularSchedule(UUID regularId, UUID userId) {
    return regularScheduleRepository
        .findByIdAndUserId(regularId, userId)
        .orElseThrow(() -> new TripFitException(ScheduleErrorCode.REGULAR_SCHEDULE_NOT_FOUND));
  }

  // 개별 일정 슬롯 단위 오버라이드 upsert — slots/uncertain 각각 있는 것만 부분 반영, 삭제 경로 없음(O1.4)
  @Transactional
  public PersonalScheduleResponse upsertPersonal(
      UUID userId,
      UpdatePersonalScheduleRequest request) {
    User user = userLookupService.requireUser(userId);
    List<PersonalScheduleItem> items =
        request.items() == null ? List.of() : request.items();
    if (items.isEmpty()) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    requireNoDuplicateDates(items);

    // 1. 값 검증부터 전부 끝내 — 잘못된 항목이 있으면 아래 구간 SELECT 전에 즉시 실패
    for (PersonalScheduleItem item : items) {
      validatePersonalItem(item);
    }

    List<LocalDate> dates =
        items.stream().map(PersonalScheduleItem::scheduleDate).sorted().toList();
    LocalDate minDate = dates.getFirst();
    LocalDate maxDate = dates.getLast();
    // 구간 내 기존 row를 한 번에 로드해 인덱싱 — 항목 수만큼 개별 SELECT를 반복하지 않는다
    Map<LocalDate, PersonalSchedule> existingByDate =
        personalScheduleRepository
            .findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(userId, minDate, maxDate)
            .stream()
            .collect(Collectors.toMap(PersonalSchedule::getScheduleDate, Function.identity()));

    // 2. 항목마다 find-or-create 후 slots 있으면 슬롯만, uncertain 있으면 그것만 갱신(둘 다 있으면 둘 다) — row는 절대 삭제되지 않는다
    for (PersonalScheduleItem item : items) {
      PersonalSchedule existing = existingByDate.get(item.scheduleDate());
      if (existing == null) {
        SlotUpdate slots = item.slots();
        PersonalSchedule created =
            personalScheduleRepository.save(
                PersonalSchedule.create(
                    user,
                    item.scheduleDate(),
                    slots != null ? slots.morningStatus() : null,
                    slots != null ? slots.afternoonStatus() : null,
                    slots != null ? slots.eveningStatus() : null,
                    item.uncertain() != null && item.uncertain()));
        existingByDate.put(item.scheduleDate(), created);
      } else {
        if (item.slots() != null) {
          existing.applySlots(
              item.slots().morningStatus(),
              item.slots().afternoonStatus(),
              item.slots().eveningStatus());
        }
        if (item.uncertain() != null) {
          existing.applyUncertain(item.uncertain());
        }
      }
    }

    // 3. 삭제 경로가 없어 upsert는 항상 일어남 — is_all_free를 false로 전이
    userSummaryService.clearAllFreeOnScheduleAdded(user);
    return buildPersonalResponse(
        user,
        dates,
        minDate,
        maxDate,
        existingByDate.values());
  }

  // 방금 반영한 날짜들의 최종 확정값(정기+개별+구글 합친 값)을 계산해 응답 — 아무 신호도 없는 날짜는 생략
  // personals는 upsertPersonal이 이미 로드·반영한 구간 전체를 그대로 넘겨받아 재조회하지 않는다
  // resolve()는 반영된 날짜(dates)만 순회 — minDate~maxDate는 googleBusy 조회 범위로만 쓰인다
  private PersonalScheduleResponse buildPersonalResponse(
      User user,
      List<LocalDate> dates,
      LocalDate minDate,
      LocalDate maxDate,
      Collection<PersonalSchedule> personals) {
    List<RegularSchedule> regulars =
        regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(user.getId());
    Map<LocalDate, GoogleCalendarBusyDay> googleBusy =
        googleCalendarService.findBusyDaysByUserId(user.getId(), minDate, maxDate);

    Map<LocalDate, UUID> idsByDate =
        personals.stream()
            .collect(Collectors.toMap(PersonalSchedule::getScheduleDate, PersonalSchedule::getId));
    Map<LocalDate, CalendarDayResponse> resolvedByDate =
        ScheduleCalendarResolver.resolve(
            regulars,
            new ArrayList<>(personals),
            dates,
            googleBusy,
            holidayProvider.findHolidaysBetween(minDate, maxDate),
            user.isHolidayRest())
            .stream()
            .collect(Collectors.toMap(CalendarDayResponse::date, Function.identity()));

    List<PersonalScheduleItemResponse> items = new ArrayList<>();
    for (LocalDate date : dates) {
      CalendarDayResponse resolved = resolvedByDate.get(date);
      if (resolved == null) {
        continue;
      }
      items.add(
          new PersonalScheduleItemResponse(
              idsByDate.get(date),
              date,
              resolved.morningStatus(),
              resolved.afternoonStatus(),
              resolved.eveningStatus(),
              resolved.uncertain()));
    }
    return new PersonalScheduleResponse(items);
  }

  // 같은 scheduleDate가 배열에 중복되면 어느 값이 최종인지 모호하므로 거부
  private static void requireNoDuplicateDates(List<PersonalScheduleItem> items) {
    Set<LocalDate> seen = new HashSet<>();
    for (PersonalScheduleItem item : items) {
      if (!seen.add(item.scheduleDate())) {
        throw new TripFitException(CommonErrorCode.INVALID_INPUT);
      }
    }
  }

  // 합산 달력 조회 — 정기 일정 미등록도 403 없음, 일정 없는 날은 응답에서 날짜 키 생략(sparse)
  // sparse day(키 생략)를 POSSIBLE로 해석하는 것은 여행방 UI·추천 쪽 — 본 API는 날짜 키 자체를 omit
  @Transactional(readOnly = true)
  public ScheduleCalendarResponse getCalendar(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate) {
    // 1. 조회 구간이 today ~ max(today+2년−1, 참여 중 ONGOING 여행 endRange 최댓값) 안에 있는지 검증
    validateCalendarDateRange(userId, startDate, endDate);

    // 2. regular·personal을 읽어 날짜별로 정기+개별을 합침
    User user = userLookupService.requireUser(userId);
    List<RegularSchedule> regulars =
        regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(userId);
    List<PersonalSchedule> personals =
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            userId,
            startDate,
            endDate);
    return new ScheduleCalendarResponse(
        startDate,
        endDate,
        ScheduleCalendarResolver.resolve(
            regulars,
            personals,
            startDate,
            endDate,
            googleCalendarService.findBusyDaysByUserId(userId, startDate, endDate),
            holidayProvider.findHolidaysBetween(startDate, endDate),
            user.isHolidayRest()));
  }

  private void validateRegularTimes(
      String title,
      String daysOfWeek,
      LocalTime startTime,
      LocalTime endTime) {
    if (title == null || title.isBlank()) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    try {
      Weekday.normalizeCsv(daysOfWeek);
    } catch (IllegalArgumentException ex) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private void validateVacationPolicy(Integer maxVacationDays) {
    if (maxVacationDays != null
        && (maxVacationDays < 0 || maxVacationDays > User.MAX_VACATION_DAYS_LIMIT)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private static String normalizeDaysOfWeek(String daysOfWeek) {
    try {
      return Weekday.normalizeCsv(daysOfWeek);
    } catch (IllegalArgumentException ex) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  // slots·uncertain 둘 다 없으면 뭘 바꾸라는 요청인지 알 수 없어 거부
  private void validatePersonalItem(PersonalScheduleItem item) {
    if (item.slots() == null && item.uncertain() == null) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    if (item.slots() != null) {
      requireSlotStatus(item.slots().morningStatus());
      requireSlotStatus(item.slots().afternoonStatus());
      requireSlotStatus(item.slots().eveningStatus());
    }
  }

  // 슬롯 오버라이드 값 검증 — POSSIBLE/IMPOSSIBLE만 허용(ON_LEAVE 등은 추후 wave, enum 값 제한은 Bean Validation으로 표현
  // 불가)
  private void requireSlotStatus(ScheduleStatus status) {
    if (status != ScheduleStatus.POSSIBLE && status != ScheduleStatus.IMPOSSIBLE) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private void validateDateRange(LocalDate startDate, LocalDate endDate) {
    if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  // 달력 조회 구간 검증 — [today, 동적 상한] 범위 밖이거나 today 이전이면 400
  private void validateCalendarDateRange(UUID userId, LocalDate startDate, LocalDate endDate) {
    validateDateRange(startDate, endDate);
    LocalDate today = LocalDate.now();
    LocalDate windowEnd =
        resolveCalendarWindowEnd(
            today,
            tripMemberRepository.findMaxOngoingEndRangeByUserId(userId));
    if (startDate.isBefore(today) || endDate.isAfter(windowEnd)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  // 마이페이지 달력 상한 계산 — 기본 today+2년−1과 참여 중 ONGOING 여행 endRange 최댓값 중 더 늦은 날짜
  public static LocalDate resolveCalendarWindowEnd(LocalDate today, LocalDate maxOngoingEndRange) {
    LocalDate baseWindowEnd = today.plusYears(CALENDAR_WINDOW_YEARS).minusDays(1);
    return maxOngoingEndRange != null && maxOngoingEndRange.isAfter(baseWindowEnd)
        ? maxOngoingEndRange
        : baseWindowEnd;
  }

  private RegularScheduleResponse toRegularResponse(RegularSchedule schedule) {
    var slots = schedule.getSlotStatuses();
    return new RegularScheduleResponse(
        schedule.getId(),
        schedule.getTitle(),
        schedule.getDaysOfWeek(),
        schedule.getStartTime(),
        schedule.getEndTime(),
        slots != null ? slots.getMorningStatus() : null,
        slots != null ? slots.getAfternoonStatus() : null,
        slots != null ? slots.getEveningStatus() : null);
  }

  private VacationPolicyResponse toVacationPolicyResponse(User user) {
    return new VacationPolicyResponse(
        user.getMaxVacationDays(),
        user.getVacationApplyPeriod(),
        user.isHalfVacationAvailable(),
        user.isHolidayRest());
  }
}
