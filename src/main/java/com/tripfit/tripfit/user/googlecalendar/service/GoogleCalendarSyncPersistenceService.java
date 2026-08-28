package com.tripfit.tripfit.user.googlecalendar.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.user.domain.User;
import com.tripfit.tripfit.user.googlecalendar.client.GoogleFreeBusyInterval;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarBusyDay;
import com.tripfit.tripfit.user.googlecalendar.domain.GoogleCalendarCredential;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarBusyDayRepository;
import com.tripfit.tripfit.user.googlecalendar.repository.GoogleCalendarCredentialRepository;
import com.tripfit.tripfit.user.googlecalendar.service.GoogleCalendarBusyMapper.SlotBusyFlags;
import com.tripfit.tripfit.user.service.UserLookupService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleCalendarSyncPersistenceService {

  private final GoogleCalendarCredentialRepository credentialRepository;

  private final GoogleCalendarBusyDayRepository busyDayRepository;

  private final UserLookupService userLookupService;

  @Transactional
  public void saveConnectedCredential(
      UUID userId,
      String refreshCiphertext,
      String accessCiphertext,
      Instant accessTokenExpiresAt,
      String googleAccountEmail) {
    User user = userLookupService.requireUser(userId);
    GoogleCalendarCredential credential =
        credentialRepository
            .findByUser_Id(userId)
            .map(
                existing -> {
                  existing.updateTokens(
                      refreshCiphertext,
                      accessCiphertext,
                      accessTokenExpiresAt,
                      googleAccountEmail);
                  return existing;
                })
            .orElseGet(
                () -> GoogleCalendarCredential.create(
                    user,
                    refreshCiphertext,
                    accessCiphertext,
                    accessTokenExpiresAt,
                    googleAccountEmail));

    credentialRepository.save(credential);
    user.connectGoogleCalendar();
  }

  @Transactional
  public void applySyncSuccess(
      UUID userId,
      AccessTokenResolution resolution,
      LocalDate windowStart,
      LocalDate windowEnd,
      List<GoogleFreeBusyInterval> intervals) {
    GoogleCalendarCredential credential = credentialRepository.findByUser_Id(userId).orElse(null);
    if (credential == null) {

      return;
    }
    if (resolution.refreshedAccessCiphertext() != null) {
      credential.updateAccessTokenCache(
          resolution.refreshedAccessCiphertext(),
          resolution.refreshedAccessExpiresAt());
      if (resolution.refreshedRefreshCiphertext() != null) {
        credential.applyRotatedRefreshToken(resolution.refreshedRefreshCiphertext());
      }
    }
    replaceBusyDays(credential.getUser(), windowStart, windowEnd, intervals);
    credential.markSynced();
  }

  @Transactional
  public void applySyncError(UUID userId, String maskedMessage) {
    credentialRepository
        .findByUser_Id(userId)
        .ifPresent(credential -> credential.markSyncError(maskedMessage));
  }

  @Transactional
  public void disconnectGoogleCalendar(UUID userId) {
    credentialRepository.deleteByUser_Id(userId);
    busyDayRepository.deleteByUser_Id(userId);
    userLookupService.requireUser(userId).disconnectGoogleCalendar();
  }

  @Transactional
  public void clearConnectedFlag(UUID userId) {
    userLookupService.requireUser(userId).disconnectGoogleCalendar();
  }

  private void replaceBusyDays(
      User user,
      LocalDate windowStart,
      LocalDate windowEnd,
      List<GoogleFreeBusyInterval> intervals) {
    UUID userId = user.getId();
    busyDayRepository.deleteByUser_IdAndScheduleDateBefore(userId, windowStart);
    busyDayRepository.deleteByUser_IdAndScheduleDateAfter(userId, windowEnd);

    Map<LocalDate, SlotBusyFlags> mapped = GoogleCalendarBusyMapper.mapIntervalsToDays(intervals);
    List<GoogleCalendarBusyDay> existing =
        busyDayRepository.findByUser_IdAndScheduleDateBetweenOrderByScheduleDateAsc(
            userId,
            windowStart,
            windowEnd);
    Map<LocalDate, GoogleCalendarBusyDay> existingByDate =
        GoogleCalendarBusyMapper.indexBusyDays(existing);

    for (Map.Entry<LocalDate, SlotBusyFlags> entry : mapped.entrySet()) {
      LocalDate date = entry.getKey();
      SlotBusyFlags flags = entry.getValue();
      GoogleCalendarBusyDay day = existingByDate.remove(date);
      if (day == null) {
        busyDayRepository.save(GoogleCalendarBusyMapper.toEntity(user, date, flags));
      } else {
        day.apply(flags.isMorningBusy(), flags.isAfternoonBusy(), flags.isEveningBusy());
      }
    }
    busyDayRepository.deleteAll(existingByDate.values());
  }
}
