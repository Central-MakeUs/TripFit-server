package com.tripfit.tripfit.notification.service;

import lombok.RequiredArgsConstructor;
import com.tripfit.tripfit.common.exception.TripFitException;
import com.tripfit.tripfit.notification.domain.NotificationHistory;
import com.tripfit.tripfit.notification.dto.NotificationResponse;
import com.tripfit.tripfit.notification.exception.NotificationErrorCode;
import com.tripfit.tripfit.notification.repository.NotificationHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor

public class NotificationQueryService {

  private final NotificationHistoryRepository notificationHistoryRepository;

  @Transactional(readOnly = true)
  public List<NotificationResponse> listRecent(UUID userId) {
    LocalDateTime since = LocalDateTime.now().minusDays(7);
    return notificationHistoryRepository
        .findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(userId, since)
        .stream()
        .map(NotificationResponse::from)
        .toList();
  }

  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    NotificationHistory history =
        notificationHistoryRepository
            .findByIdAndUser_Id(notificationId, userId)
            .orElseThrow(() -> new TripFitException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    history.markRead(LocalDateTime.now());
  }
}
