package com.tripfit.tripfit.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.trip.domain.ScheduleStatus;
import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripMember;
import com.tripfit.tripfit.trip.domain.TripMemberRole;
import com.tripfit.tripfit.trip.domain.TripMemberScheduleSnapshot;
import com.tripfit.tripfit.trip.domain.TripMemberStatus;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripMemberScheduleSnapshotRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.schedule.domain.RegularSchedule;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripScheduleSnapshotServiceTest {

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private TripMemberScheduleSnapshotRepository snapshotRepository;

  @Mock
  private RegularScheduleRepository regularScheduleRepository;

  @Mock
  private PersonalScheduleRepository personalScheduleRepository;

  private TripScheduleSnapshotService snapshotService;

  private User user;

  private Trip trip;

  @BeforeEach
  void setUp() {
    snapshotService =
        new TripScheduleSnapshotService(
            tripMemberRepository,
            snapshotRepository,
            regularScheduleRepository,
            personalScheduleRepository);
    user = new User("sub", SocialProvider.GOOGLE, "a@b.c", "nick", null);
    user.setId(USER_ID);
    user.setLastName("홍");
    user.setFirstName("길동");
    trip =
        new Trip(
            user,
            "제주",
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 7),
            3,
            4,
            "ABC234",
            TripStatus.ONGOING);
    trip.setId(TRIP_ID);
  }

  @Test
  void freezeTrip_savesSparseEffectiveDays() {
    TripMember member =
        new TripMember(trip, user, TripMemberRole.OWNER, TripMemberStatus.RESPONDED,
            LocalDateTime.now());
    when(snapshotRepository.existsByTrip_Id(TRIP_ID)).thenReturn(false);
    when(tripMemberRepository.findByTripIdAndDeletedAtIsNull(TRIP_ID)).thenReturn(List.of(member));
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
            trip.getStartRange(),
            trip.getEndRange()))
        .thenReturn(List.of());

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
