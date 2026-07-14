package com.tripfit.tripfit.user.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.exception.CommonErrorCode;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.exception.TripErrorCode;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.schedule.domain.PersonalSchedule;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.domain.VacationApplyPeriod;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.dto.RegularScheduleResponse;
import com.tripfit.tripfit.user.schedule.dto.CreateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest;
import com.tripfit.tripfit.user.schedule.dto.UpdatePersonalScheduleRequest.PersonalScheduleItem;
import com.tripfit.tripfit.user.schedule.dto.UpdateRegularScheduleRequest;
import com.tripfit.tripfit.user.schedule.exception.ScheduleErrorCode;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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

  private static final UUID OTHER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  private static final UUID REGULAR_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440099");

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @InjectMocks
  private ScheduleService scheduleService;

  private User user;

  @BeforeEach
  void setUp() {
    user = new User("google-sub", SocialProvider.GOOGLE, "user@example.com", "홍길동", null);
    user.setId(USER_ID);
    user.setFirstName("길동");
    user.setLastName("홍");
  }

  @Test
  void createRegular_computesSlotStatusesViaSharedTimeSlot() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
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
                LocalTime.of(18, 0),
                5,
                VacationApplyPeriod.ONE_WEEK_BEFORE,
                true,
                true));

    assertThat(response.morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(response.afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(response.eveningStatus()).isEqualTo(ScheduleStatus.POSSIBLE);
    assertThat(response.maxVacationDays()).isEqualTo(5);
    assertThat(response.vacationApplyPeriod()).isEqualTo(VacationApplyPeriod.ONE_WEEK_BEFORE);
  }

  @Test
  void createRegular_appliesVacationDefaultsWhenOmitted() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
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
                LocalTime.of(18, 0),
                null,
                null,
                null,
                null));

    assertThat(response.maxVacationDays()).isEqualTo(2);
    assertThat(response.vacationApplyPeriod()).isNull();
    assertThat(response.halfVacationAvailable()).isFalse();
    assertThat(response.holidayRest()).isTrue();
  }

  @Test
  void updateRegular_recalculatesSlotsFromTimes() {
    RegularSchedule existing =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            5,
            VacationApplyPeriod.ONE_WEEK_BEFORE,
            true,
            true);
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
                LocalTime.of(22, 0),
                3,
                VacationApplyPeriod.TWO_WEEKS_BEFORE,
                false,
                false));

    assertThat(existing.getTitle()).isEqualTo("야간 근무");
    assertThat(existing.getDaysOfWeek()).isEqualTo("MON,WED,FRI");
    assertThat(existing.getStartTime()).isEqualTo(LocalTime.of(13, 0));
    assertThat(existing.getEndTime()).isEqualTo(LocalTime.of(22, 0));
    assertThat(existing.getMaxVacationDays()).isEqualTo(3);
    assertThat(existing.getVacationApplyPeriod())
        .isEqualTo(VacationApplyPeriod.TWO_WEEKS_BEFORE);
    assertThat(existing.isHalfVacationAvailable()).isFalse();
    assertThat(existing.isHolidayRest()).isFalse();
    assertThat(response.afternoonStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    assertThat(response.eveningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void upsertPersonal_dateLevelUncertain() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(regularScheduleRepository.existsByUserId(USER_ID)).thenReturn(true);
    when(
        personalScheduleRepository.findByUserIdAndScheduleDate(
            USER_ID,
            LocalDate.of(2026, 8, 3)))
        .thenReturn(Optional.empty());
    when(personalScheduleRepository.save(any(PersonalSchedule.class)))
        .thenAnswer(
            invocation -> {
              PersonalSchedule s = invocation.getArgument(0);
              s.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440088"));
              return s;
            });
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 3)))
        .thenAnswer(
            inv -> {
              PersonalSchedule saved =
                  PersonalSchedule.create(
                      user,
                      LocalDate.of(2026, 8, 3),
                      ScheduleStatus.IMPOSSIBLE,
                      ScheduleStatus.POSSIBLE,
                      ScheduleStatus.POSSIBLE,
                      true);
              saved.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440088"));
              return List.of(saved);
            });

    var response =
        scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        LocalDate.of(2026, 8, 3),
                        ScheduleStatus.IMPOSSIBLE,
                        ScheduleStatus.POSSIBLE,
                        ScheduleStatus.POSSIBLE,
                        true))));

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().uncertain()).isTrue();
    assertThat(response.items().getFirst().morningStatus()).isEqualTo(ScheduleStatus.IMPOSSIBLE);
    ArgumentCaptor<PersonalSchedule> captor = ArgumentCaptor.forClass(PersonalSchedule.class);
    verify(personalScheduleRepository).save(captor.capture());
    assertThat(captor.getValue().isUncertain()).isTrue();
  }

  @Test
  void upsertPersonal_withoutRegular_throws403() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(regularScheduleRepository.existsByUserId(USER_ID)).thenReturn(false);

    assertThatThrownBy(
        () -> scheduleService.upsertPersonal(
            USER_ID,
            new UpdatePersonalScheduleRequest(
                List.of(
                    new PersonalScheduleItem(
                        LocalDate.of(2026, 8, 3),
                        ScheduleStatus.POSSIBLE,
                        ScheduleStatus.POSSIBLE,
                        ScheduleStatus.POSSIBLE,
                        false)))))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(ScheduleErrorCode.REGULAR_SCHEDULE_REQUIRED);
  }

  @Test
  void getPersonal_withoutRegular_throws403() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(regularScheduleRepository.existsByUserId(USER_ID)).thenReturn(false);

    assertThatThrownBy(
        () -> scheduleService.getPersonal(
            USER_ID,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 7)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(ScheduleErrorCode.REGULAR_SCHEDULE_REQUIRED);
  }

  @Test
  void requireRegularScheduleRegistered_whenMissing_throws403() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(regularScheduleRepository.existsByUserId(USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> scheduleService.requireRegularScheduleRegistered(USER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(ScheduleErrorCode.REGULAR_SCHEDULE_REQUIRED);
  }

  @Test
  void getCalendar_whenRangeExceedsTwoYears_throws400() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(regularScheduleRepository.existsByUserId(USER_ID)).thenReturn(true);

    assertThatThrownBy(
        () -> scheduleService.getCalendar(
            USER_ID,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2028, 1, 2)))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(CommonErrorCode.INVALID_INPUT);
  }

  @Test
  void getCalendar_resolvesSparseWeekdays() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(regularScheduleRepository.existsByUserId(USER_ID)).thenReturn(true);
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0),
            2,
            null,
            false,
            true);
    when(regularScheduleRepository.findByUserIdOrderByCreatedAtAsc(USER_ID))
        .thenReturn(List.of(work));
    when(
        personalScheduleRepository.findByUserIdAndScheduleDateBetweenOrderByScheduleDateAsc(
            USER_ID,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 7)))
        .thenReturn(List.of());

    var response =
        scheduleService.getCalendar(
            USER_ID,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 7));

    assertThat(response.days()).hasSize(5);
    assertThat(response.days().getFirst().date()).isEqualTo(LocalDate.of(2026, 8, 3));
  }

  @Test
  void getMemberPersonalSummary_nonMember_throws() {
    Trip trip =
        new Trip(
            user,
            "제주",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 10),
            4,
            6,
            "ABC123",
            TripStatus.ONGOING);
    trip.setId(TRIP_ID);
    when(tripRepository.findByIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(Optional.of(trip));
    when(tripMemberRepository.existsByTripIdAndUserId(TRIP_ID, OTHER_ID)).thenReturn(false);

    assertThatThrownBy(() -> scheduleService.getMemberPersonalSummary(TRIP_ID, OTHER_ID))
        .isInstanceOf(TripFitException.class)
        .extracting(ex -> ((TripFitException) ex).getErrorCode())
        .isEqualTo(TripErrorCode.TRIP_ACCESS_DENIED);
  }
}
