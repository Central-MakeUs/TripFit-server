package com.tripfit.tripfit.user.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.auth.service.RefreshTokenService;
import com.tripfit.tripfit.trip.service.TripService;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.schedule.repository.PersonalScheduleRepository;
import com.tripfit.tripfit.user.schedule.repository.RegularScheduleRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWithdrawalPersistenceService {

  private final UserLookupService userLookupService;

  private final TripService tripService;

  private final PersonalScheduleRepository personalScheduleRepository;

  private final RegularScheduleRepository regularScheduleRepository;

  private final GoogleCalendarCredentialRepository googleCalendarCredentialRepository;

  private final GoogleCalendarBusyDayRepository googleCalendarBusyDayRepository;

  private final RefreshTokenService refreshTokenService;

  @Transactional
  public void finalizeWithdrawal(UUID userId) {
    User user = userLookupService.requireUser(userId);
    if (user.getDeletedAt() != null) {

      return;
    }

    tripService.leaveAllActiveTripsAsMember(userId);
    tripService.deleteAllOwnedActiveTrips(userId);

    personalScheduleRepository.deleteByUserId(userId);
    regularScheduleRepository.deleteByUserId(userId);
    googleCalendarCredentialRepository.deleteByUser_Id(userId);
    googleCalendarBusyDayRepository.deleteByUser_Id(userId);
    refreshTokenService.revokeAllForUser(userId);

    user.scrubPiiForWithdrawal();
  }
}
