package com.tripfit.tripfit.trip.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.common.holiday.HolidayProvider;
import com.tripfit.tripfit.trip.schedule.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.membership.domain.TripMember;
import com.tripfit.tripfit.trip.membership.domain.TripMemberRole;
import com.tripfit.tripfit.trip.schedule.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.membership.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.port.out.GoogleCalendarPort;
import com.tripfit.tripfit.trip.port.out.SchedulePort;
import com.tripfit.tripfit.trip.port.out.UserDirectoryPort;
import com.tripfit.tripfit.trip.membership.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.schedule.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarPortAdapter;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarService;
import com.tripfit.tripfit.user.repository.UserRepository;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import com.tripfit.tripfit.user.schedule.service.ScheduleAvailabilityAdapter;
import com.tripfit.tripfit.trip.service.TripServiceSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripScheduleSnapshotServiceTest {

  private final HolidayProvider holidayProvider = (start, end) -> Set.of();

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private TripMemberScheduleSnapshotRepository snapshotRepository;

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  @Mock
  private GoogleCalendarService googleCalendarService;

  @Mock
  private UserDirectoryPort userDirectoryPort;

  @Mock
  private UserRepository userRepository;

  private TripScheduleSnapshotService snapshotService;

  private User user;

  private Trip trip;

  @BeforeEach
  void setUp() {
    TripServiceSupport support =
        new TripServiceSupport(tripRepository, tripMemberRepository, userDirectoryPort);
    SchedulePort schedulePort =
        new ScheduleAvailabilityAdapter(
            regularScheduleRepository, personalScheduleRepository, userRepository,
            holidayProvider);
    GoogleCalendarPort googleCalendarPort = new GoogleCalendarPortAdapter(googleCalendarService);
    snapshotService =
        new TripScheduleSnapshotService(
            snapshotRepository,
            schedulePort,
            googleCalendarPort,
            support);
    user = new User("sub", SocialProvider.GOOGLE, "a@b.c", "nick", null);
    user.setId(USER_ID);
    user.applyProfilePatch("길동", "홍", null);
    trip =
        new Trip(
            user,
            "제주",
            LocalDate.now(),
            LocalDate.now().plusDays(4),
            2,
            3,
            4,
            "ABC234",
            TripStatus.ONGOING);
    trip.setId(TRIP_ID);
  }

  @Test
  void freezeTrip_savesSparseEffectiveDays() {
    TripMember member =
        new TripMember(trip, user, TripMemberRole.OWNER, TripMemberStatus.ACTIVE,
            LocalDateTime.now());
    when(snapshotRepository.existsByTrip_Id(TRIP_ID)).thenReturn(false);
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(List.of(member));
    // 연차·반차·공휴일 휴무는 이제 User 소유 값
    user.applyVacationPolicy(2, null, false, true);
    RegularSchedule work =
        RegularSchedule.create(
            user,
            "출근",
            "MON,TUE,WED,THU,FRI",
            LocalTime.of(9, 0),
            LocalTime.of(18, 0));
    when(regularScheduleRepository.findByUserIdIn(List.of(USER_ID)))
        .thenReturn(List.of(work));
    when(userRepository.findAllById(List.of(USER_ID))).thenReturn(List.of(user));
    when(
        personalScheduleRepository.findByUserIdInAndScheduleDateBetween(
            List.of(USER_ID),
            trip.getStartRange(),
            trip.getEndRange()))
        .thenReturn(List.of());
    when(
        googleCalendarService.findBusyDaysByUserIds(
            List.of(USER_ID),
            trip.getStartRange(),
            trip.getEndRange()))
        .thenReturn(java.util.Map.of());

    snapshotService.freezeTrip(trip);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<TripMemberScheduleSnapshot>> captor = ArgumentCaptor.forClass(List.class);
    verify(snapshotRepository).saveAll(captor.capture());
    List<TripMemberScheduleSnapshot> saved = captor.getValue();
    assertThat(saved).isNotEmpty();
    assertThat(saved.getFirst().getSlotStatuses().getMorningStatus())
        .isEqualTo(ScheduleStatus.IMPOSSIBLE);
  }

  @Test
  void freezeTrip_whenAlreadyFrozen_isNoOp() {
    when(snapshotRepository.existsByTrip_Id(TRIP_ID)).thenReturn(true);

    snapshotService.freezeTrip(trip);

    verify(snapshotRepository, never()).saveAll(anyList());
    verify(tripMemberRepository, never()).findByTripIdAndDeletedAtIsNull(TRIP_ID);
  }
}
