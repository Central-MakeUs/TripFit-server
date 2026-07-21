package com.tripfit.tripfit.trip.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.trip.domain.Trip;
import com.tripfit.tripfit.trip.domain.TripStatus;
import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import com.tripfit.tripfit.user.domain.SocialProvider;
import com.tripfit.tripfit.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripHomeMaintenanceServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 7, 19);

  private static final UUID TRIP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @Mock
  private TripScheduleSnapshotService tripScheduleSnapshotService;

  @InjectMocks
  private TripHomeMaintenanceService tripHomeMaintenanceService;

  @Test
  void runForDate_freezesThenTerminatesExpiredOngoing() {
    User owner = new User("sub", SocialProvider.GOOGLE, "a@b.c", "n", null);
    owner.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
    Trip trip =
        new Trip(
            owner,
            "제주",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 10),
            3,
            4,
            "ABC234",
            TripStatus.ONGOING);
    trip.setId(TRIP_ID);
    when(tripRepository.findExpiredOngoing(TODAY)).thenReturn(List.of(trip));
    when(tripMemberRepository.clearExpiredPins(TODAY)).thenReturn(1);

    tripHomeMaintenanceService.runForDate(TODAY);

    verify(tripScheduleSnapshotService).freezeTrip(trip);
    org.assertj.core.api.Assertions.assertThat(trip.getStatus()).isEqualTo(TripStatus.TERMINATED);
    verify(tripMemberRepository).clearExpiredPins(TODAY);
  }

  @Test
  void runForDate_whenNothingExpired_onlyClearsPins() {
    when(tripRepository.findExpiredOngoing(TODAY)).thenReturn(List.of());
    when(tripMemberRepository.clearExpiredPins(TODAY)).thenReturn(0);

    tripHomeMaintenanceService.runForDate(TODAY);

    verify(tripScheduleSnapshotService, never()).freezeTrip(any());
    verify(tripMemberRepository).clearExpiredPins(TODAY);
  }
}
