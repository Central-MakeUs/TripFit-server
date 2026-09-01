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

@Service
@RequiredArgsConstructor
public class ScheduleService {

  public static final int CALENDAR_WINDOW_YEARS = 2;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserLookupService userLookupService;

  private final GoogleCalendarService googleCalendarService;

  private final TripMemberRepository tripMemberRepository;

  private final HolidayProvider holidayProvider;

  // 특정 사용자의 정기 일정 목록을 생성일 오름차순으로 조회합니다.
  @Transactional(readOnly = true)
  public RegularScheduleListResponse listRegular(UUID userId) {
    return new RegularScheduleListResponse(
        regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .map(this::toRegularResponse)
            .toList());
  }

  // 정기 일정을 생성합니다. 요일 정보(csv)와 시간대를 정규화/검증한 뒤 저장합니다.
  @Transactional
  public RegularScheduleResponse createRegular(UUID userId, CreateRegularScheduleRequest request) {

    String normalizedDaysOfWeek =
        validateAndNormalizeRegularTimes(
            request.title(),
            request.daysOfWeek(),
            request.startTime(),
            request.endTime());

    User user = userLookupService.requireUser(userId);
    RegularSchedule schedule =
        RegularSchedule.create(
            user,
            request.title().trim(),
            normalizedDaysOfWeek,
            request.startTime(),
            request.endTime());
    regularScheduleRepository.save(schedule);
    return toRegularResponse(schedule);
  }

  // 특정 정기 일정을 덮어씁니다(전체 수정).
  @Transactional
  public RegularScheduleResponse updateRegular(
      UUID userId,
      UUID regularId,
      UpdateRegularScheduleRequest request) {
    String normalizedDaysOfWeek =
        validateAndNormalizeRegularTimes(
            request.title(),
            request.daysOfWeek(),
            request.startTime(),
            request.endTime());
    RegularSchedule schedule = requireOwnedRegularSchedule(regularId, userId);
    schedule.applyUpdate(
        request.title().trim(),
        normalizedDaysOfWeek,
        request.startTime(),
        request.endTime());
    return toRegularResponse(schedule);
  }

  // 사용자의 연차 및 휴일 정책을 반환합니다.
  @Transactional(readOnly = true)
  public VacationPolicyResponse getVacationPolicy(UUID userId) {
    return toVacationPolicyResponse(userLookupService.requireUser(userId));
  }

  // 연차 정책 정보를 수정(교체)합니다.
  @Transactional
  public VacationPolicyResponse updateVacationPolicy(
      UUID userId,
      UpdateVacationPolicyRequest request) {
    validateVacationPolicy(request.maxVacationDays());
    User user = userLookupService.requireUser(userId);
    user.applyVacationPolicy(
        request.maxVacationDays(),
        request.vacationApplyPeriod(),
        request.halfVacationAvailable(),
        request.holidayRest());
    return toVacationPolicyResponse(user);
  }

  @Transactional
  public void deleteRegular(UUID userId, UUID regularId) {
    RegularSchedule schedule = requireOwnedRegularSchedule(regularId, userId);
    regularScheduleRepository.delete(schedule);
  }

  @Transactional
  public void deleteAllRegular(UUID userId) {
    regularScheduleRepository.deleteByUserId(userId);
  }

  private RegularSchedule requireOwnedRegularSchedule(UUID regularId, UUID userId) {
    return regularScheduleRepository
        .findByIdAndUserId(regularId, userId)
        .orElseThrow(() -> new TripFitException(ScheduleErrorCode.REGULAR_SCHEDULE_NOT_FOUND));
  }

  // 개별 일정을 Upsert 합니다.
  @Transactional
  public PersonalScheduleResponse upsertPersonal(
      UUID userId,
      UpdatePersonalScheduleRequest request) {
    // 1. 요청된 항목 검증(중복된 날짜나 허용 윈도우 밖 등 검사)
    User user = userLookupService.requireUser(userId);
    List<PersonalScheduleItem> items =
        request.items() == null ? List.of() : request.items();
    if (items.isEmpty()) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    requireNoDuplicateDates(items);

    for (PersonalScheduleItem item : items) {
      validatePersonalItem(item);
    }

    List<LocalDate> dates =
        items.stream().map(PersonalScheduleItem::scheduleDate).sorted().toList();
    LocalDate minDate = dates.getFirst();
    LocalDate maxDate = dates.getLast();

    validateCalendarDateRange(userId, minDate, maxDate);

    // 2. 이미 존재하는 날짜면 덮어쓰고, 없으면 새로 저장(Upsert 방식)
    Map<LocalDate, PersonalSchedule> existingByDate =
        personalScheduleRepository
            .findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(userId, minDate, maxDate)
            .stream()
            .collect(Collectors.toMap(PersonalSchedule::getScheduleDate, Function.identity()));

    for (PersonalScheduleItem item : items) {
      PersonalSchedule existing = existingByDate.get(item.scheduleDate());
      if (existing == null) {
        // 3. 기존 일정이 없는 경우: 새로 생성
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
        // 4. 기존 일정이 있는 경우: 수정
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

    return buildPersonalResponse(
        user,
        dates,
        minDate,
        maxDate,
        existingByDate.values());
  }

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

  private static void requireNoDuplicateDates(List<PersonalScheduleItem> items) {
    Set<LocalDate> seen = new HashSet<>();
    for (PersonalScheduleItem item : items) {
      if (!seen.add(item.scheduleDate())) {
        throw new TripFitException(CommonErrorCode.INVALID_INPUT);
      }
    }
  }

  // 정기, 개별, 그리고 외부 캘린더 일정을 병합하여
  // 지정된 기간(startDate ~ endDate)의 최종 달력을 조회합니다.
  @Transactional(readOnly = true)
  public ScheduleCalendarResponse getCalendar(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate) {

    validateCalendarDateRange(userId, startDate, endDate);

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

  private static String validateAndNormalizeRegularTimes(
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
      return Weekday.normalizeCsv(daysOfWeek);
    } catch (IllegalArgumentException ex) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private void validateVacationPolicy(int maxVacationDays) {
    if (maxVacationDays < 0 || maxVacationDays > User.MAX_VACATION_DAYS_LIMIT) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

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
