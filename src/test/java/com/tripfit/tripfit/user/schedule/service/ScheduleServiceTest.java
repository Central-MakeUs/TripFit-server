package com.tripfit.tripfit.user.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.CreateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest.PersonalScheduleItem;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest.SlotUpdate;
import com.tripfit.tripfit.user.schedule.dto.UpdateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdateVacationPolicyRequest;
import com.tripfit.tripfit.user.schedule.dto.VacationPolicyResponse;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.service.UserLookupService;
import com.tripfit.tripfit.user.service.UserSummaryService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  private static final UUID REGULAR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private UserLookupService userLookupService;

  @Mock
  private UserSummaryService userSummaryService;

  @Mock
  private GoogleCalendarService googleCalendarService;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private HolidayProvider holidayProvider;

  @InjectMocks
  private ScheduleService scheduleService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
    user.setId(USER_ID);
    user.applyProfilePatch("길동", "홍", null);
  }

  @Test
  void createRegular_computesSlotStatusesViaSharedTimeSlot() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(regularScheduleRepository.save(any(RegularSchedule.class)))
        .thenAnswer(
            invocation -> {
              RegularSchedule s = invocation.getArgument(0);
              s.setId(REGULAR_ID);
              return s;
            });

    RegularScheduleResponse response =
        scheduleService.createRegular(
            USER_ID,
            new CreateRegularScheduleRequest(
                "출근",
                "MON,TUE,WED,THU,FRI",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)));

    assertThat(response.morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(response.afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(response.eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    // maxVacationDays·vacationApplyPeriod는 RegularScheduleResponse에서 제거되고
    // User/VacationPolicyResponse로 이동
  }

  @Test
  void createRegular_appliesVacationDefaultsWhenOmitted() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(regularScheduleRepository.save(any(RegularSchedule.class)))
        .thenAnswer(
            invocation -> {
              RegularSchedule s = invocation.getArgument(0);
              s.setId(REGULAR_ID);
              return s;
            });

    RegularScheduleResponse response =
        scheduleService.createRegular(
            USER_ID,
            new CreateRegularScheduleRequest(
                "출근",
                "MON,TUE,WED,THU,FRI",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)));

    // maxVacationDays·vacationApplyPeriod·halfVacationAvailable·holidayRest 기본값 검증은
    // RegularScheduleResponse에서 제거되고 User(VacationPolicyResponse)로 이동 — createRegular는 더 이상
    // 이 값들을 다루지 않는다
    assertThat(response.title()).isEqualTo("출근");
  }

  @Test
  void createRegular_rejectsInvalidWeekday() {
    assertThatThrownBy(
        () -> scheduleService.createRegular(
            USER_ID,
            new CreateRegularScheduleRequest(
                "출근",
                "MON,FOO",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0))))
        .isInstanceOf(TripFitException.class);
  }

  @Test
  void createRegular_normalizesDaysOfWeek() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(regularScheduleRepository.save(any(RegularSchedule.class)))
        .thenAnswer(
            invocation -> {
              RegularSchedule s = invocation.getArgument(0);
              s.setId(REGULAR_ID);
              return s;
            });

    RegularScheduleResponse response =
        scheduleService.createRegular(
            USER_ID,
            new CreateRegularScheduleRequest(
                "출근",
                " mon, tue ",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0)));

    assertThat(response.daysOfWeek()).isEqualTo("MON,TUE");
  }

  @Test
  void updateRegular_recalculatesSlotsFromTimes() {
    // 연차·반차·공휴일 휴무는 이제 RegularSchedule이 아니라 User 소유 값
    user.applyVacationPolicy(5, VacationApplyPeriod.ONE_WEEK_BEFORE, true, true);
    RegularSchedule existing =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    existing.setId(REGULAR_ID);
    when(regularScheduleRepository.findByIdAndUserId(REGULAR_ID, USER_ID))
        .thenReturn(Optional.of(existing));

    RegularScheduleResponse response =
        scheduleService.updateRegular(
            USER_ID,
            REGULAR_ID,
            new UpdateRegularScheduleRequest(
                "야간 근무",
                "MON,WED,FRI",
                LocalTime.of(13, 0),
                LocalTime.of(22, 0)));

    assertThat(existing.getTitle()).isEqualTo("야간 근무");
    assertThat(existing.getDaysOfWeek()).isEqualTo("MON,WED,FRI");
    assertThat(existing.getStartTime()).isEqualTo(LocalTime.of(13, 0));
    assertThat(existing.getEndTime()).isEqualTo(LocalTime.of(22, 0));
    // maxVacationDays·vacationApplyPeriod·halfVacationAvailable·holidayRest는 RegularSchedule에서
    // 제거되고 User로 이동 — updateRegular는 더 이상 이 값들을 다루지 않는다(전용 PATCH vacation-policy)
    assertThat(response.afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(response.eveningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void getVacationPolicy_returnsUserOwnedValues() {
    user.applyVacationPolicy(5, VacationApplyPeriod.ONE_WEEK_BEFORE, true, false);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    VacationPolicyResponse response = scheduleService.getVacationPolicy(USER_ID);

    assertThat(response.maxVacationDays()).isEqualTo(5);
    assertThat(response.vacationApplyPeriod()).isEqualTo(VacationApplyPeriod.ONE_WEEK_BEFORE);
    assertThat(response.halfVacationAvailable()).isTrue();
    assertThat(response.holidayRest()).isFalse();
  }

  @Test
  void updateVacationPolicy_replacesAllFieldsOnUser() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    VacationPolicyResponse response =
        scheduleService.updateVacationPolicy(
            USER_ID,
            new UpdateVacationPolicyRequest(3, VacationApplyPeriod.TWO_WEEKS_BEFORE, true, false));

    assertThat(response.maxVacationDays()).isEqualTo(3);
    assertThat(response.vacationApplyPeriod()).isEqualTo(VacationApplyPeriod.TWO_WEEKS_BEFORE);
    assertThat(response.halfVacationAvailable()).isTrue();
    assertThat(response.holidayRest()).isFalse();
  }

  @Test
  void updateVacationPolicy_nullFields_fallBackToDefaults() {
    user.applyVacationPolicy(5, VacationApplyPeriod.ONE_WEEK_BEFORE, true, false);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    VacationPolicyResponse response =
        scheduleService.updateVacationPolicy(
            USER_ID,
            new UpdateVacationPolicyRequest(null, null, null, null));

    assertThat(response.maxVacationDays()).isEqualTo(User.DEFAULT_MAX_VACATION_DAYS);
    assertThat(response.vacationApplyPeriod()).isNull();
    assertThat(response.halfVacationAvailable()).isFalse();
    assertThat(response.holidayRest()).isTrue();
  }

  @Test
  void updateVacationPolicy_maxVacationDaysOutOfRange_throwsInvalidInput() {
    assertThatThrownBy(
        () -> scheduleService.updateVacationPolicy(
            USER_ID,
            new UpdateVacationPolicyRequest(11, null, null, null)))
        .isInstanceOf(TripFitException.class);
  }

  @Test
  void upsertPersonal_slotsOnly_createsRowWithUncertainFalse() {
    LocalDate date = LocalDate.now().plusDays(3);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            date,
            date))
        .thenReturn(List.of());
    when(personalScheduleRepository.save(any(PersonalSchedule.class)))
        .thenAnswer(
            invocation -> {
              PersonalSchedule s = invocation.getArgument(0);
              s.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440088"));
              return s;
            });
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, date, date)).thenReturn(Map.of());

    var response =
        scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        date,
                        new SlotUpdate(
                            ScheduleStatus.IMPOSSIBLE,
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE),
                        null))));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().uncertain()).isFalse();
    ArgumentCaptor<PersonalSchedule> captor = ArgumentCaptor.forClass(PersonalSchedule.class);
    verify(personalScheduleRepository).save(captor.capture());
    assertThat(captor.getValue().isUncertain()).isFalse();
    assertThat(captor.getValue().getSlotStatuses().getMorningStatus())
        .isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void upsertPersonal_uncertainOnly_existingRow_preservesSlots() {
    LocalDate date = LocalDate.now().plusDays(3);
    PersonalSchedule existing =
        PersonalSchedule.create(
            user,
            date,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            false);
    existing.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440088"));
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            date,
            date))
        .thenReturn(List.of(existing));
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, date, date)).thenReturn(Map.of());

    // uncertain만 보내면 slots 필드 자체가 없으므로 기존 오버라이드는 절대 안 건드려야 한다(UI 동작 확인 2·3번)
    var response =
        scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(new PersonalScheduleItem(date, null, true))));

    verify(personalScheduleRepository, never()).save(any(PersonalSchedule.class));
    assertThat(existing.isUncertain()).isTrue();
    assertThat(existing.getSlotStatuses().getMorningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(existing.getSlotStatuses().getAfternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(response.items().getFirst().uncertain()).isTrue();
  }

  @Test
  void upsertPersonal_uncertainToggleOnThenOff_slotsNeverChange() {
    LocalDate date = LocalDate.now().plusDays(3);
    PersonalSchedule existing =
        PersonalSchedule.create(
            user,
            date,
            ScheduleStatus.IMPOSSIBLE,
            ScheduleStatus.POSSIBLE,
            ScheduleStatus.POSSIBLE,
            false);
    existing.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440088"));
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            date,
            date))
        .thenReturn(List.of(existing));
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, date, date)).thenReturn(Map.of());

    scheduleService.upsertPersonal(
        USER_ID,
        new UpdatePersonalScheduleRequest(List.of(new PersonalScheduleItem(date, null, true))));
    assertThat(existing.isUncertain()).isTrue();
    assertThat(existing.getSlotStatuses().getMorningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);

    scheduleService.upsertPersonal(
        USER_ID,
        new UpdatePersonalScheduleRequest(List.of(new PersonalScheduleItem(date, null, false))));
    assertThat(existing.isUncertain()).isFalse();
    assertThat(existing.getSlotStatuses().getMorningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(existing.getSlotStatuses().getAfternoonStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(existing.getSlotStatuses().getEveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void upsertPersonal_slotsAndUncertainTogether_bothApplied() {
    LocalDate date = LocalDate.now().plusDays(3);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            date,
            date))
        .thenReturn(List.of());
    when(personalScheduleRepository.save(any(PersonalSchedule.class)))
        .thenAnswer(
            invocation -> {
              PersonalSchedule s = invocation.getArgument(0);
              s.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440088"));
              return s;
            });
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, date, date)).thenReturn(Map.of());

    var response =
        scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        date,
                        new SlotUpdate(
                            ScheduleStatus.IMPOSSIBLE,
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE),
                        true))));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().uncertain()).isTrue();
    assertThat(response.items().getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void upsertPersonal_explicitAllPossibleOnWorkday_notDeleted_o13BugRegression() {
    // 정기(근무일: 아침·오후 불가능)가 있는 날짜에 슬롯 3개를 전부 POSSIBLE로 명시해도
    // (구 O1.3의 CLEAR 오인 버그와 달리) row가 삭제되지 않고 그대로 저장돼야 한다
    LocalDate thursday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.THURSDAY));
    user.applyVacationPolicy(2, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            thursday,
            thursday))
        .thenReturn(List.of());
    when(personalScheduleRepository.save(any(PersonalSchedule.class)))
        .thenAnswer(
            invocation -> {
              PersonalSchedule s = invocation.getArgument(0);
              s.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440088"));
              return s;
            });
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID))
        .thenReturn(List.of(work));
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, thursday, thursday))
        .thenReturn(Map.of());

    var response =
        scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        thursday,
                        new SlotUpdate(
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE),
                        null))));

    verify(personalScheduleRepository).save(any(PersonalSchedule.class));
    assertThat(response.items()).hasSize(1);
    var item = response.items().getFirst();
    assertThat(item.id()).isNotNull();
    assertThat(item.morningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
  }

  @Test
  void upsertPersonal_slotsAndUncertainBothMissing_throws400() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    LocalDate date = LocalDate.now().plusDays(3);

    assertThatThrownBy(
        () -> scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(List.of(new PersonalScheduleItem(date, null, null)))))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void upsertPersonal_slotsFieldMissing_throws400() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    LocalDate date = LocalDate.now().plusDays(3);

    assertThatThrownBy(
        () -> scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        date,
                        new SlotUpdate(null, ScheduleStatus.POSSIBLE, ScheduleStatus.POSSIBLE),
                        null)))))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void upsertPersonal_duplicateScheduleDate_throws400() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    LocalDate date = LocalDate.now().plusDays(3);

    assertThatThrownBy(
        () -> scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(date, null, true),
                    new PersonalScheduleItem(
                        date,
                        new SlotUpdate(
                            ScheduleStatus.POSSIBLE, ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE),
                        null)))))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void upsertPersonal_rejectsEmptyItems() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);

    assertThatThrownBy(
        () -> scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(List.of())))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void getCalendar_whenStartBeforeToday_throws400() {
    LocalDate today = LocalDate.now();
    assertThatThrownBy(
        () -> scheduleService.getCalendar(USER_ID, today.minusDays(1), today.plusDays(7)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void getCalendar_whenEndAfterWindow_throws400() {
    LocalDate today = LocalDate.now();
    LocalDate windowEnd = today.plusYears(2).minusDays(1);
    assertThatThrownBy(
        () -> scheduleService.getCalendar(USER_ID, today, windowEnd.plusDays(1)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  // 저장은 되는데 GET /calendar로는 못 보는 일정이 생기지 않도록, 저장에도 조회와 같은 상한을 적용한다
  @Test
  void upsertPersonal_whenDateAfterWindowEnd_throws400() {
    LocalDate beyondWindow = LocalDate.now().plusYears(2).plusDays(30);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(tripMemberRepository.findMaxOngoingEndRangeByUserId(USER_ID)).thenReturn(null);

    assertThatThrownBy(
        () -> scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        beyondWindow,
                        new SlotUpdate(
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE),
                        null)))))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);

    verify(personalScheduleRepository, never()).save(any(PersonalSchedule.class));
  }

  // 지난 날짜도 같은 이유로 막는다 — 조회 구간이 오늘부터라 저장해도 다시 열어볼 수 없다
  @Test
  void upsertPersonal_whenDateBeforeToday_throws400() {
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(tripMemberRepository.findMaxOngoingEndRangeByUserId(USER_ID)).thenReturn(null);

    assertThatThrownBy(
        () -> scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        LocalDate.now().minusDays(1),
                        new SlotUpdate(
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE),
                        null)))))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  // 상한은 조회와 같은 함수로 계산하므로, 참여 중인 ONGOING 여행방 종료일까지 늘어나는 규칙이 저장에도 그대로 적용된다
  @Test
  void upsertPersonal_whenOngoingTripExtendsWindow_allowsDateBeyondTwoYears() {
    LocalDate extendedEnd = LocalDate.now().plusYears(2).plusDays(30);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(tripMemberRepository.findMaxOngoingEndRangeByUserId(USER_ID)).thenReturn(extendedEnd);
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            extendedEnd,
            extendedEnd))
        .thenReturn(List.of());
    when(personalScheduleRepository.save(any(PersonalSchedule.class)))
        .thenAnswer(
            invocation -> {
              PersonalSchedule saved = invocation.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, extendedEnd, extendedEnd))
        .thenReturn(Map.of());

    var response =
        scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        extendedEnd,
                        new SlotUpdate(
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE,
                            ScheduleStatus.POSSIBLE),
                        null))));

    assertThat(response.items()).hasSize(1);
    verify(personalScheduleRepository).save(any(PersonalSchedule.class));
  }

  @Test
  void getCalendar_whenOngoingTripEndRangeBeyondWindow_extendsWindowEnd() {
    LocalDate today = LocalDate.now();
    LocalDate baseWindowEnd = today.plusYears(2).minusDays(1);
    LocalDate extendedEnd = baseWindowEnd.plusDays(30);
    when(tripMemberRepository.findMaxOngoingEndRangeByUserId(USER_ID)).thenReturn(extendedEnd);
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of());
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            today,
            extendedEnd))
        .thenReturn(List.of());
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, today, extendedEnd))
        .thenReturn(Map.of());

    var response = scheduleService.getCalendar(USER_ID, today, extendedEnd);

    assertThat(response.endDate()).isEqualTo(extendedEnd);
  }

  @Test
  void getCalendar_whenEndAfterExtendedWindow_throws400() {
    LocalDate today = LocalDate.now();
    LocalDate baseWindowEnd = today.plusYears(2).minusDays(1);
    LocalDate extendedEnd = baseWindowEnd.plusDays(30);
    when(tripMemberRepository.findMaxOngoingEndRangeByUserId(USER_ID)).thenReturn(extendedEnd);

    assertThatThrownBy(
        () -> scheduleService.getCalendar(USER_ID, today, extendedEnd.plusDays(1)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void getCalendar_resolvesSparseWeekdays() {
    LocalDate start = LocalDate.now().plusDays(1);
    // 다음 월요일부터 7일 — 주중 5일만 regular가 펼쳐짐
    while (start.getDayOfWeek().getValue() != 1) {
      start = start.plusDays(1);
    }
    LocalDate end = start.plusDays(6);

    user.applyVacationPolicy(2, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    when(userLookupService.requireUser(USER_ID)).thenReturn(user);
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID))
        .thenReturn(List.of(work));
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            start,
            end))
        .thenReturn(List.of());
    when(googleCalendarService.findBusyDaysByUserId(USER_ID, start, end)).thenReturn(Map.of());

    var response = scheduleService.getCalendar(USER_ID, start, end);

    assertThat(response.days()).hasSize(5);
    assertThat(response.days().getFirst().date()).isEqualTo(start);
  }
}
