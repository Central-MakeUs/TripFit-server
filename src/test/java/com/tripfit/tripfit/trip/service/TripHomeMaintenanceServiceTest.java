package com.tripfit.tripfit.trip.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripfit.tripfit.trip.repository.TripMemberRepository;
import com.tripfit.tripfit.trip.repository.TripRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripHomeMaintenanceServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 7, 19);

  @Mock
  private TripRepository tripRepository;

  @Mock
  private TripMemberRepository tripMemberRepository;

  @InjectMocks
  private TripHomeMaintenanceService tripHomeMaintenanceService;

  @Test
  void runForDate_terminatesOngoingThenClearsExpiredPins() {
    when(tripRepository.terminateExpiredOngoing(TODAY)).thenReturn(2);
    when(tripMemberRepository.clearExpiredPins(TODAY)).thenReturn(1);

    tripHomeMaintenanceService.runForDate(TODAY);

    InOrder order = inOrder(tripRepository, tripMemberRepository);
    order.verify(tripRepository).terminateExpiredOngoing(TODAY);
    order.verify(tripMemberRepository).clearExpiredPins(TODAY);
  }

  @Test
  void runForDate_isIdempotentWhenNothingToUpdate() {
    when(tripRepository.terminateExpiredOngoing(TODAY)).thenReturn(0);
    when(tripMemberRepository.clearExpiredPins(TODAY)).thenReturn(0);

    tripHomeMaintenanceService.runForDate(TODAY);

    verify(tripRepository).terminateExpiredOngoing(TODAY);
    verify(tripMemberRepository).clearExpiredPins(TODAY);
  }
}
