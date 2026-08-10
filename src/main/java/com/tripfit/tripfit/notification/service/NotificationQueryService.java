package com.tripfit.tripfit.notification.service;

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
// 알림센터 목록 조회·읽음 처리(D5)
public class NotificationQueryService {

  private final NotificationHistoryRepository notificationHistoryRepository;

  public NotificationQueryService(NotificationHistoryRepository notificationHistoryRepository) {
    this.notificationHistoryRepository = notificationHistoryRepository;
  }

  // 최근 7일 알림 이력을 최신순으로 조회한다(D9)
  @Transactional(readOnly = true)
  public List<NotificationResponse> listRecent(UUID userId) {
    LocalDateTime since = LocalDateTime.now().minusDays(7);
    return notificationHistoryRepository
        .findByUser_IdAndSentAtGreaterThanEqualOrderBySentAtDesc(userId, since)
        .stream()
        .map(NotificationResponse::from)
        .toList();
  }

  // 본인 알림을 읽음 처리한다 — 이미 읽음이면 idempotent
  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    NotificationHistory history =
        notificationHistoryRepository
            .findByIdAndUser_Id(notificationId, userId)
            .orElseThrow(() -> new TripFitException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    history.markRead(LocalDateTime.now());
  }
}
