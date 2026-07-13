package com.tripfit.tripfit.user.schedule.service;

import com.tripfit.tripfit.auth.exception.AuthErrorCode;
import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse.DayPersonal;
import com.tripfit.tripfit.trip.dto.MemberPersonalSummaryResponse.MemberPersonal;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.dto.CreateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.PersonalScheduleResponse.PersonalScheduleItemResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse.RegularScheduleListResponse;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest.PersonalScheduleItem;
import com.tripfit.tripfit.user.schedule.dto.UpdateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.exception.ScheduleErrorCode;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

  private final RegularScheduleRepository regularScheduleRepository;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final UserRepository userRepository;

  private final TripRepository tripRepository;

  private final TripMemberRepository tripMemberRepository;

  public ScheduleService(
      RegularScheduleRepository regularScheduleRepository,
      PersonalScheduleRepository personalScheduleRepository,
      UserRepository userRepository,
      TripRepository tripRepository,
      TripMemberRepository tripMemberRepository) {
    this.regularScheduleRepository = regularScheduleRepository;
    this.personalScheduleRepository = personalScheduleRepository;
    this.userRepository = userRepository;
    this.tripRepository = tripRepository;
    this.tripMemberRepository = tripMemberRepository;
  }

  // 사용자의 정기 일정 목록을 생성 시각 오름차순으로 조회함
  @Transactional(readOnly = true)
  public RegularScheduleListResponse listRegular(UUID userId) {
    return new RegularScheduleListResponse(
        regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
            .map(this::toRegularResponse)
            .toList());
  }

  // 정기 일정을 생성하고 슬롯을 시각 구간으로 계산한 뒤 등록 플래그를 true로 둠
  @Transactional
  public RegularScheduleResponse createRegular(UUID userId, CreateRegularScheduleRequest request) {
    // 1. 제목·시각·연차 필드 입력을 검증함
    validateCreateRegular(request);

    // 2. start/end로 슬롯을 계산해 정기 일정을 저장함
    User user = findUser(userId);
    RegularSchedule schedule =
        RegularSchedule.create(
            user,
            request.title().trim(),
            request.daysOfWeek(),
            request.startTime(),
            request.endTime(),
            request.maxVacationDays(),
            request.vacationApplyPeriod(),
            request.halfVacationAvailable(),
            request.holidayRest());
    regularScheduleRepository.save(schedule);

    // 3. 정기 일정이 생겼으므로 온보딩 게이트 플래그를 켬
    user.setScheduleRegistered(true);
    return toRegularResponse(schedule);
  }

  // 정기 일정 전체를 수정하고 start/end로 슬롯을 재계산함
  @Transactional
  public RegularScheduleResponse updateRegular(
      UUID userId,
      UUID regularId,
      UpdateRegularScheduleRequest request) {
    validateUpdateRegular(request);
    RegularSchedule schedule =
        regularScheduleRepository
            .findByIdAndUserId(regularId, userId)
            .orElseThrow(() -> new TripFitException(ScheduleErrorCode.REGULAR_SCHEDULE_NOT_FOUND));
    schedule.applyUpdate(
        request.title().trim(),
        request.daysOfWeek(),
        request.startTime(),
        request.endTime(),
        request.maxVacationDays(),
        request.vacationApplyPeriod(),
        request.halfVacationAvailable(),
        request.holidayRest());
    return toRegularResponse(schedule);
  }

  // 정기 일정을 삭제하고 남은 행 여부로 isScheduleRegistered를 재계산함
  @Transactional
  public void deleteRegular(UUID userId, UUID regularId) {
    // 1. 본인 소유 행만 삭제함
    RegularSchedule schedule =
        regularScheduleRepository
            .findByIdAndUserId(regularId, userId)
            .orElseThrow(() -> new TripFitException(ScheduleErrorCode.REGULAR_SCHEDULE_NOT_FOUND));
    regularScheduleRepository.delete(schedule);

    // 2. 남은 정기 일정이 없으면 등록 플래그를 false로 되돌림
    User user = findUser(userId);
    user.setScheduleRegistered(regularScheduleRepository.existsByUserId(userId));
  }

  // 기간 내 개인 일정을 날짜 오름차순으로 조회함 (정기 등록 게이트 — BR-USER-006)
  @Transactional(readOnly = true)
  public PersonalScheduleResponse getPersonal(
      UUID userId,
      LocalDate startDate,
      LocalDate endDate) {
    // 1. 정기 일정 미등록이면 개별 일정 진입을 차단함
    requireRegularScheduleRegistered(userId);
    validateDateRange(startDate, endDate);
    List<PersonalScheduleItemResponse> items =
        personalScheduleRepository
            .findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(userId, startDate, endDate)
            .stream()
            .map(this::toPersonalItem)
            .toList();
    return new PersonalScheduleResponse(items);
  }

  // 개인 일정을 날짜별로 생성 또는 갱신한 뒤 요청 기간 목록을 반환함 (정기 등록 게이트 — BR-USER-006)
  @Transactional
  public PersonalScheduleResponse upsertPersonal(
      UUID userId,
      UpdatePersonalScheduleRequest request) {
    // 1. 정기 일정 미등록이면 개별 일정 입력을 차단함
    requireRegularScheduleRegistered(userId);
    User user = findUser(userId);
    LocalDate minDate = null;
    LocalDate maxDate = null;

    // 2. 항목마다 검증 후 (user, date) 기준으로 insert 또는 update함
    for (PersonalScheduleItem item : request.items()) {
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

    // 3. 요청에 포함된 날짜 범위의 최신 목록을 반환함
    return getPersonal(userId, minDate, maxDate);
  }

  // 여행방 멤버들의 희망 기간 개인 일정을 집계함
  @Transactional(readOnly = true)
  public MemberPersonalSummaryResponse getMemberPersonalSummary(
      UUID tripId,
      UUID requesterUserId) {
    // 1. 여행방 존재·요청자 멤버십을 확인함
    Trip trip =
        tripRepository
            .findByIdAndDeletedAtIsNull(tripId)
            .orElseThrow(() -> new TripFitException(TripErrorCode.TRIP_NOT_FOUND));
    if (!tripMemberRepository.existsByTripIdAndUserId(tripId, requesterUserId)) {
      throw new TripFitException(TripErrorCode.TRIP_ACCESS_DENIED);
    }

    // 2. 멤버 userId와 기간 내 personal_schedule을 조회함
    List<TripMember> members = tripMemberRepository.findByTripId(tripId);
    List<UUID> userIds = members.stream().map(m -> m.getUser().getId()).distinct().toList();
    if (userIds.isEmpty()) {
      return new MemberPersonalSummaryResponse(List.of());
    }

    List<PersonalSchedule> schedules =
        personalScheduleRepository.findByUserIdInAndScheduleDateBetween(
            userIds,
            trip.getStartRange(),
            trip.getEndRange());
    Map<UUID, List<PersonalSchedule>> byUserId =
        schedules.stream().collect(Collectors.groupingBy(s -> s.getUser().getId()));

    // 3. 멤버별로 표시명과 날짜 슬롯을 묶어 응답을 만듦
    Map<UUID, User> usersById = new LinkedHashMap<>();
    for (TripMember member : members) {
      usersById.putIfAbsent(member.getUser().getId(), member.getUser());
    }

    List<MemberPersonal> result = new ArrayList<>();
    for (Map.Entry<UUID, User> entry : usersById.entrySet()) {
      List<DayPersonal> days =
          byUserId.getOrDefault(entry.getKey(), List.of()).stream()
              .sorted((a, b) -> a.getScheduleDate().compareTo(b.getScheduleDate()))
              .map(
                  s -> new DayPersonal(
                      s.getScheduleDate(),
                      s.getSlotStatuses().getMorningStatus(),
                      s.getSlotStatuses().getAfternoonStatus(),
                      s.getSlotStatuses().getEveningStatus(),
                      s.isUncertain()))
              .toList();
      result.add(new MemberPersonal(entry.getKey(), displayName(entry.getValue()), days));
    }
    return new MemberPersonalSummaryResponse(result);
  }

  // 여행방 일정 응답 등에서 정기 일정 등록 여부를 강제함 (BR-USER-006)
  @Transactional(readOnly = true)
  public void requireRegularScheduleRegistered(UUID userId) {
    User user = findUser(userId);
    if (!user.isScheduleRegistered() && !regularScheduleRepository.existsByUserId(userId)) {
      throw new TripFitException(ScheduleErrorCode.REGULAR_SCHEDULE_REQUIRED);
    }
  }

  // 정기 일정 생성 요청의 필수·범위 값을 검증함
  private void validateCreateRegular(CreateRegularScheduleRequest request) {
    validateRegularTimesAndVacation(
        request.title(),
        request.startTime(),
        request.endTime(),
        request.maxVacationDays());
  }

  // 정기 일정 수정 요청의 필수·범위 값을 검증함
  private void validateUpdateRegular(UpdateRegularScheduleRequest request) {
    validateRegularTimesAndVacation(
        request.title(),
        request.startTime(),
        request.endTime(),
        request.maxVacationDays());
  }

  private void validateRegularTimesAndVacation(
      String title,
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
  }

  // 개인 일정 항목의 날짜·슬롯 필수 값을 검증함
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

  // 슬롯 상태는 POSSIBLE/IMPOSSIBLE만 허용함
  private void requireSlotStatus(ScheduleStatus status) {
    if (status != ScheduleStatus.POSSIBLE && status != ScheduleStatus.IMPOSSIBLE) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  // 조회 기간이 유효한지 확인함
  private void validateDateRange(LocalDate startDate, LocalDate endDate) {
    if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
      throw new TripFitException(CommonErrorCode.INVALID_INPUT);
    }
  }

  // RegularSchedule 엔티티를 API 응답으로 변환함
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

  // PersonalSchedule 엔티티를 API 항목 응답으로 변환함
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

  // 프로필 성·이름 → nickname → 기본 표시명 순으로 멤버 이름을 정함
  static String displayName(User user) {
    if (user.hasProfileNameComplete()) {
      return user.getLastName() + user.getFirstName();
    }
    if (user.getNickname() != null && !user.getNickname().isBlank()) {
      return user.getNickname();
    }
    return "사용자";
  }

  // JWT userId로 사용자를 조회하고 없으면 403을 던짐
  private User findUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new TripFitException(AuthErrorCode.AUTH_FORBIDDEN));
  }
}
