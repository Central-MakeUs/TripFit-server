package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.ScheduleStatus;
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
import com.tripfit.tripfit.user.schedule.dto.UpdateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.exception.ScheduleErrorCode;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사용자 정기·개별 일정 CRUD와 합산 달력 조회 — 정기 일정 없이 개별만 등록 가능
// hasPreSchedule은 본 Service 응답에 없음 — row INSERT/DELETE 후 UserSummaryService EXISTS → GET /auth/me 등
// 재조회
@Service
public class ScheduleService {

  // 마이페이지 달력 조회 가능 기간(년) — today ~ today+2년−1
  public static final int CALENDAR_WINDOW_YEARS = 2;

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserLookupService userLookupService;

  private final UserSummaryService userSummaryService;

  private final GoogleCalendarService googleCalendarService;

  public ScheduleService(
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      UserLookupService userLookupService,
      UserSummaryService userSummaryService,
      GoogleCalendarService googleCalendarService) {
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.userLookupService = userLookupService;
    this.userSummaryService = userSummaryService;
    this.googleCalendarService = googleCalendarService;
  }

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
    // 1. 제목·시각·연차 필드 입력을 검증함
    validateCreateRegular(request);

    // 2. start/end로 슬롯을 계산해 정기 일정을 저장함
    User user = userLookupService.requireUser(userId);
    RegularSchedule schedule =
        RegularSchedule.create(
            user,
            request.title().trim(),
            normalizeDaysOfWeek(request.daysOfWeek()),
            request.startTime(),
            request.endTime(),
            request.maxVacationDays(),
            request.vacationApplyPeriod(),
            request.halfVacationAvailable(),
            request.holidayRest());
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
    validateUpdateRegular(request);
    RegularSchedule schedule = requireOwnedRegularSchedule(regularId, userId);
    schedule.applyUpdate(
        request.title().trim(),
        normalizeDaysOfWeek(request.daysOfWeek()),
        request.startTime(),
        request.endTime(),
        request.maxVacationDays(),
        request.vacationApplyPeriod(),
        request.halfVacationAvailable(),
        request.holidayRest());
    return toRegularResponse(schedule);
  }

  // 정기 일정 삭제 — regular 0건 + personal 0건이면 hasPreSchedule false (다음 login/me/profile)
  @Transactional
  public void deleteRegular(UUID userId, UUID regularId) {
    RegularSchedule schedule = requireOwnedRegularSchedule(regularId, userId);
    regularScheduleRepository.delete(schedule);
    userSummaryService.markAllFreeIfSchedulesCleared(userLookupService.requireUser(userId));
  }

  // 본인 소유 정기 일정 로드 — 없거나 타인 소유면 REGULAR_SCHEDULE_NOT_FOUND
  private RegularSchedule requireOwnedRegularSchedule(UUID regularId, UUID userId) {
    return regularScheduleRepository
        .findByIdAndUserId(regularId, userId)
        .orElseThrow(() -> new TripFitException(ScheduleErrorCode.REGULAR_SCHEDULE_NOT_FOUND));
  }

  // 개별 일정 기간 조회 — 정기 일정 없이 개별만 있어도 허용
  @Transactional(readOnly = true)
  public PersonalScheduleResponse getPersonal(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate) {
    validateDateRange(startDate, endDate);
    List<PersonalScheduleItemResponse> items =
        personalScheduleRepository
            .findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(userId, startDate, endDate)
            .stream()
            .map(this::toPersonalItem)
            .toList();
    return new PersonalScheduleResponse(items);
  }

  // 개별 일정 일괄 저장·삭제 — items upsert와 deletedDates 삭제를 한 요청에서 처리
  @Transactional
  public PersonalScheduleResponse upsertPersonal(
      UUID userId,
      UpdatePersonalScheduleRequest request) {
    User user = userLookupService.requireUser(userId);
    List<PersonalScheduleItem> items =
        request.items() == null ? List.of() : request.items();
    List<LocalDate> deletedDates =
        request.deletedDates() == null ? List.of() : request.deletedDates();

    if (items.isEmpty() && deletedDates.isEmpty()) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }

    // items와 deletedDates에 같은 날짜가 있으면 400
    for (PersonalScheduleItem item : items) {
      if (deletedDates.contains(item.scheduleDate())) {
        throw new TripFitException(CommonErrorCode.INVALID_INPUT);
      }
    }

    LocalDate minDate = null;
    LocalDate maxDate = null;

    // 1. deletedDates에 해당하는 날짜 row를 먼저 삭제
    if (!deletedDates.isEmpty()) {
      personalScheduleRepository.deleteByUserIdAndScheduleDateIn(userId, deletedDates);
      for (LocalDate d : deletedDates) {
        if (minDate == null || d.isBefore(minDate)) {
          minDate = d;
        }
        if (maxDate == null || d.isAfter(maxDate)) {
          maxDate = d;
        }
      }
    }

    // 2. items를 insert 또는 update
    for (PersonalScheduleItem item : items) {
      validatePersonalItem(item);
      PersonalSchedule existing =
          personalScheduleRepository
              .findByUserIdAndScheduleDate(userId, item.scheduleDate())
              .orElse(null);
      if (existing == null) {
        personalScheduleRepository.save(
            PersonalSchedule.create(
                user,
                item.scheduleDate(),
                item.morningStatus(),
                item.afternoonStatus(),
                item.eveningStatus(),
                item.uncertain()));
      } else {
        existing.apply(
            item.morningStatus(),
            item.afternoonStatus(),
            item.eveningStatus(),
            item.uncertain());
      }
      if (minDate == null || item.scheduleDate().isBefore(minDate)) {
        minDate = item.scheduleDate();
      }
      if (maxDate == null || item.scheduleDate().isAfter(maxDate)) {
        maxDate = item.scheduleDate();
      }
    }

    // 3. is_all_free 전이 — 추가 시 false · 삭제 후 0행이면 true
    if (!items.isEmpty()) {
      userSummaryService.clearAllFreeOnScheduleAdded(user);
    }
    if (!deletedDates.isEmpty()) {
      userSummaryService.markAllFreeIfSchedulesCleared(user);
    }
    return getPersonal(userId, minDate, maxDate);
  }

  // 합산 달력 조회 — 정기 일정 미등록도 403 없음, 일정 없는 날은 응답에서 날짜 키 생략(sparse)
  // sparse day(키 생략)를 POSSIBLE로 해석하는 것은 여행방 UI·추천 쪽 — 본 API는 날짜 키 자체를 omit
  @Transactional(readOnly = true)
  public ScheduleCalendarResponse getCalendar(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate) {
    // 1. 조회 구간이 today ~ today+2년−1 안에 있는지 검증
    validateCalendarDateRange(startDate, endDate);

    // 2. regular·personal을 읽어 날짜별 effective로 합침
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
            googleCalendarService.findBusyDaysByUserId(userId, startDate, endDate)));
  }

  private void validateCreateRegular(CreateRegularScheduleRequest request) {
    validateRegularTimesAndVacation(
        request.title(),
        request.daysOfWeek(),
        request.startTime(),
        request.endTime(),
        request.maxVacationDays());
  }

  private void validateUpdateRegular(UpdateRegularScheduleRequest request) {
    validateRegularTimesAndVacation(
        request.title(),
        request.daysOfWeek(),
        request.startTime(),
        request.endTime(),
        request.maxVacationDays());
  }

  private void validateRegularTimesAndVacation(
      String title,
      String daysOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      Integer maxVacationDays) {
    if (title == null || title.isBlank()) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    if (maxVacationDays != null
        && (maxVacationDays < 0 || maxVacationDays > RegularSchedule.MAX_VACATION_DAYS_LIMIT)) {
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

  private static String normalizeDaysOfWeek(String daysOfWeek) {
    try {
      return Weekday.normalizeCsv(daysOfWeek);
    } catch (IllegalArgumentException ex) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private void validatePersonalItem(PersonalScheduleItem item) {
    if (item.scheduleDate() == null
        || item.morningStatus() == null
        || item.afternoonStatus() == null
        || item.eveningStatus() == null) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
    requireSlotStatus(item.morningStatus());
    requireSlotStatus(item.afternoonStatus());
    requireSlotStatus(item.eveningStatus());
  }

  // 달력 API 슬롯 값 검증 — POSSIBLE/IMPOSSIBLE만 허용, ON_LEAVE 등은 추후 wave
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

  // 달력 조회 구간 검증 — [today, today+2년−1] 범위 밖이거나 today 이전이면 400
  private void validateCalendarDateRange(LocalDate startDate, LocalDate endDate) {
    validateDateRange(startDate, endDate);
    LocalDate today = LocalDate.now();
    LocalDate windowEnd = today.plusYears(CALENDAR_WINDOW_YEARS).minusDays(1);
    if (startDate.isBefore(today) || endDate.isAfter(windowEnd)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
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
        slots != null ? slots.getEveningStatus() : null,
        schedule.getMaxVacationDays(),
        schedule.getVacationApplyPeriod(),
        schedule.isHalfVacationAvailable(),
        schedule.isHolidayRest());
  }

  private PersonalScheduleItemResponse toPersonalItem(PersonalSchedule schedule) {
    var slots = schedule.getSlotStatuses();
    return new PersonalScheduleItemResponse(
        schedule.getId(),
        schedule.getScheduleDate(),
        slots.getMorningStatus(),
        slots.getAfternoonStatus(),
        slots.getEveningStatus(),
        schedule.isUncertain());
  }

  // 사용자 표시명 결정 — 성+이름 → nickname → "사용자" 기본값
  public static String displayName(User user) {
    if (user.hasProfileNameComplete()) {
      return user.getLastName() + user.getFirstName();
    }
    if (user.getNickname() != null && !user.getNickname().isBlank()) {
      return user.getNickname();
    }
    return "사용자";
  }
}
